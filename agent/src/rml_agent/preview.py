from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import threading
import subprocess
import uuid
import zipfile

from .bridge import Bridge
from .store import canonical_uuid
from .workspace import atomic_write, digest, reject_links

_OPERATIONS = {"inspect", "command", "scenario_start", "scenario_status", "scenario_cancel"}
_COPY_DIRS = ("mods", "config", "resourcepacks", "scripts")
_MAX_LOG = 64 * 1024
_STARTUP_SECONDS = 180


class PreviewManager:
    def __init__(self, profile: Path | None, root: Path, secret_env: str = ""):
        self.profile_path = Path(profile).absolute() if profile is not None else None
        self.root = Path(root).absolute()
        self.secret_env = secret_env
        self.profile: dict | None = None
        self.previews: dict[str, dict] = {}
        self._lock = asyncio.Lock()
        self._port_lock = threading.Lock()
        self._ports: set[int] = set()
        self._closing = False

    async def initialize(self) -> None:
        reject_links(self.root)
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        if self.profile_path is not None:
            reject_links(self.profile_path)
            data = json.loads(await asyncio.to_thread(self.profile_path.read_text, encoding="utf-8"))
            if not isinstance(data, dict) or data.get("version") != 1 or data.get("mode") not in ("client", "server"):
                raise ValueError("Invalid preview launch profile")
            self.profile = data
        for child in self.root.iterdir():
            reject_links(child)
            if not child.is_dir():
                continue
            metadata_path = child / "metadata.json"
            reject_links(metadata_path)
            try:
                metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
                preview_id = canonical_uuid(metadata["preview_id"])
                owner = canonical_uuid(metadata["session_id"])
                if child.name != preview_id:
                    continue
                if metadata.get("status") == "closed":
                    continue
                metadata["directory"] = str(child.absolute())
                metadata.update(status="interrupted", error="Preview was interrupted when the service stopped")
                metadata["session_id"] = owner
                metadata["preview_id"] = preview_id
                self.previews[preview_id] = {"meta": metadata, "process": None, "bridge": None, "tasks": []}
                self._save(self.previews[preview_id])
            except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError):
                # Unknown directories are never adopted or removed.
                continue

    async def create(self, session_id: str, build_id: str, build_path: Path, sha256: str,
                     pack_id: str, seed: int, emit) -> dict:
        session_id = canonical_uuid(session_id)
        build_id = canonical_uuid(build_id)
        if self.profile is None:
            raise RuntimeError("Preview launch profile is unavailable")
        if not isinstance(sha256, str) or len(sha256) != 64 or any(c not in "0123456789abcdef" for c in sha256):
            raise ValueError("sha256 must be a lowercase SHA-256 digest")
        if not isinstance(seed, int) or isinstance(seed, bool) or not -(2**63) <= seed < 2**63:
            raise ValueError("seed must be a signed 64-bit integer")
        async with self._lock:
            for item in self.previews.values():
                process = item.get("process")
                if item["meta"].get("status") == "running" and process is not None and process.returncode is not None:
                    item["meta"]["status"] = "exited"
                    item["meta"]["error"] = f"Preview process exited with code {process.returncode}"
                    self._save(item)
            owned = [item for item in self.previews.values() if item["meta"].get("session_id") == session_id and item["meta"].get("status") in ("starting", "running", "stopping")]
            live = [item for item in self.previews.values() if item["meta"].get("status") in ("starting", "running", "stopping")]
            if owned or len(live) >= 2 or self._closing:
                raise RuntimeError("Preview capacity is unavailable")
            preview_id = str(uuid.uuid4())
            directory = self.root / preview_id
            directory.mkdir(mode=0o700)
            token = secrets.token_urlsafe(32)
            ready = directory / "ready.json"
            meta = {"preview_id": preview_id, "session_id": session_id, "build_id": build_id,
                    "sha256": sha256, "mode": self.profile["mode"], "status": "starting",
                    "directory": str(directory), "log_tail": "", "error": None, "join_address": None}
            record = {"meta": meta, "process": None, "bridge": None, "tasks": [], "token": token,
                      "ready": ready, "emit": emit}
            self.previews[preview_id] = record
            self._save(record)
        try:
            emit("preview", self._public(record))
            reject_links(Path(build_path))
            preparation = asyncio.create_task(asyncio.to_thread(self._prepare, record, Path(build_path), pack_id, seed))
            try:
                await asyncio.shield(preparation)
            except asyncio.CancelledError:
                await preparation
                raise
            argv = self._command(record)
            env = self._environment(record, token, seed)
            spawn = asyncio.create_task(asyncio.create_subprocess_exec(
                *argv, cwd=directory, env=env, stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT,
                creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)))
            try:
                process = await asyncio.shield(spawn)
            except asyncio.CancelledError:
                process = await spawn
                record["process"] = process
                await self._stop_process(record)
                raise
            record["process"] = process
            record["tasks"].append(asyncio.create_task(self._drain(record)))
            await self._wait_ready(record)
            bridge = Bridge(record["endpoint"], token)
            record["bridge"] = bridge
            meta["status"] = "running"
            meta["join_address"] = record.get("join_address")
            self._save(record)
            emit("preview", self._public(record))
            return self._public(record)
        except BaseException as exc:
            await self._stop_process(record)
            meta["status"] = "failed"
            meta["error"] = self._error_text(exc, record)
            if not await self._dispose(record):
                meta["status"] = "cleanup_failed"
                meta["error"] += "\nPreview cleanup failed; owned metadata retained"
                self._save(record)
            emit("preview", self._public(record))
            if isinstance(exc, asyncio.CancelledError):
                raise
            raise RuntimeError(meta["error"]) from exc

    async def status(self, session_id: str, preview_id: str) -> dict:
        record = self._owned(session_id, preview_id)
        process = record.get("process")
        if record["meta"]["status"] == "running" and process is not None and process.returncode is not None:
            record["meta"]["status"] = "exited"
            record["meta"]["error"] = f"Preview process exited with code {process.returncode}"
            self._save(record)
        return self._public(record)

    async def invoke(self, session_id: str, preview_id: str, operation: str, arguments: dict, emit) -> dict:
        record = self._owned(session_id, preview_id)
        if operation not in _OPERATIONS:
            raise ValueError("Unsupported preview operation")
        if record["meta"]["status"] != "running" or record.get("bridge") is None:
            raise RuntimeError("Preview is not ready")
        return await record["bridge"].invoke("console", record["meta"]["session_id"], operation, arguments, emit)

    async def close_preview(self, session_id: str, preview_id: str) -> dict:
        record = self._owned(session_id, preview_id)
        if record["meta"]["status"] == "closed":
            return self._public(record)
        closing = record.get("closing")
        if closing is None or closing.done():
            closing = asyncio.create_task(self._close_record(record))
            record["closing"] = closing
        try:
            return await asyncio.shield(closing)
        except asyncio.CancelledError:
            await closing
            raise

    async def _close_record(self, record: dict) -> dict:
        meta = record["meta"]
        meta["status"] = "stopping"
        bridge = record.get("bridge")
        if bridge is not None:
            try:
                await bridge.invoke("console", meta["session_id"], "preview_stop", {}, lambda *_: None)
            except Exception as exc:
                meta["error"] = f"Preview stop request failed: {exc}"
        await self._stop_process(record)
        removed = await self._dispose(record)
        meta["status"] = "closed" if removed else "cleanup_failed"
        if not removed:
            meta["error"] = "Could not safely remove preview directory; ownership metadata retained for retry"
            self._save(record)
        return self._public(record)

    def summaries(self, session_id: str) -> list[dict]:
        session_id = canonical_uuid(session_id)
        return [self._public(item) for item in self.previews.values() if item["meta"].get("session_id") == session_id]

    async def close(self) -> None:
        self._closing = True
        for preview_id, record in list(self.previews.items()):
            if record["meta"].get("status") != "closed":
                await self.close_preview(record["meta"]["session_id"], preview_id)

    def _owned(self, session_id: str, preview_id: str) -> dict:
        session_id, preview_id = canonical_uuid(session_id), canonical_uuid(preview_id)
        record = self.previews.get(preview_id)
        if record is None or record["meta"].get("session_id") != session_id:
            raise KeyError("Unknown preview")
        return record

    def _save(self, record: dict) -> None:
        directory = Path(record["meta"]["directory"])
        metadata = dict(record["meta"])
        atomic_write(directory / "metadata.json", json.dumps(metadata, ensure_ascii=False).encode("utf-8"))

    def _public(self, record: dict) -> dict:
        meta = record["meta"]
        return {key: meta.get(key) for key in ("preview_id", "build_id", "sha256", "mode", "status", "log_tail", "error", "directory", "join_address")}

    def _reserve_port(self) -> int:
        with self._port_lock:
            while True:
                with socket.socket() as sock:
                    sock.bind(("127.0.0.1", 0))
                    port = sock.getsockname()[1]
                if port not in self._ports:
                    self._ports.add(port)
                    return port

    def _prepare(self, record: dict, build_path: Path, pack_id: str, seed: int) -> None:
        directory = Path(record["meta"]["directory"])
        source = Path(self.profile["game_directory"]).absolute()
        reject_links(source)
        for name in _COPY_DIRS:
            src = source / name
            reject_links(src)
            if src.exists():
                self._copy_tree(src, directory / name, name, pack_id, record)
        options = source / "options.txt"
        reject_links(options)
        if options.is_file():
            lines = options.read_text(encoding="utf-8", errors="replace").splitlines()
            values = [line for line in lines if not line.startswith(("fullscreen:", "pauseOnLostFocus:"))]
            values.extend(("fullscreen:false", "pauseOnLostFocus:false"))
            atomic_write(directory / "options.txt", ("\n".join(values) + "\n").encode())
        mods = directory / "mods"
        mods.mkdir(exist_ok=True)
        build = build_path.read_bytes()
        if digest(build) != record["meta"]["sha256"]:
            raise ValueError("Build bytes do not match the requested SHA-256")
        atomic_write(mods / "rml-preview-content.zip", build)
        if record["meta"]["mode"] == "server":
            eula = source / "eula.txt"
            reject_links(eula)
            if not eula.is_file() or "eula=true" not in eula.read_text(encoding="utf-8", errors="replace").lower().splitlines():
                raise ValueError("Dedicated preview requires an already accepted parent EULA")
            atomic_write(directory / "eula.txt", eula.read_bytes())
            port = self._reserve_port()
            record["port"] = port
            record["join_address"] = f"127.0.0.1:{port}"
            properties = ("level-name=preview\nlevel-seed=" + str(seed) +
                          "\nlevel-type=flat\ngamemode=1\nserver-ip=127.0.0.1\nserver-port=" + str(port) +
                          "\nonline-mode=false\nmax-players=1\n" )
            atomic_write(directory / "server.properties", properties.encode())
        else:
            record["join_address"] = None
        record["seed"] = seed

    def _copy_tree(self, source: Path, destination: Path, category: str, pack_id: str, record: dict) -> None:
        reject_links(source)
        destination.mkdir(parents=True, exist_ok=True)
        shared = {str(Path(path).absolute()).casefold() for path in self.profile.get("shared_mods", [])}
        for item in source.iterdir():
            reject_links(item)
            if category == "config" and item.absolute() == (Path(self.profile["game_directory"]).absolute() / "config" / "rml-agent.json"):
                continue
            if category == "mods":
                if str(item.absolute()).casefold() in shared:
                    continue
                modids = self._rml_modids(item)
                if pack_id in modids:
                    if len(modids) != 1:
                        raise ValueError(f"Cannot replace package {item.name}: it contains the selected pack and other modids")
                    continue
            if item.is_dir():
                self._copy_tree(item, destination / item.name, category, pack_id, record)
            else:
                shutil.copyfile(item, destination / item.name)

    @staticmethod
    def _rml_modids(path: Path) -> set[str]:
        if path.is_dir():
            info = path / "rml.info"
            reject_links(info)
            if not info.is_file():
                return set()
            raw = info.read_bytes()
        elif zipfile.is_zipfile(path):
            try:
                with zipfile.ZipFile(path) as archive:
                    raw = archive.read("rml.info")
            except (KeyError, OSError, zipfile.BadZipFile):
                return set()
        else:
            return set()
        try:
            parsed = json.loads(raw)
        except (ValueError, UnicodeError):
            return set()
        entries = parsed if isinstance(parsed, list) else [parsed]
        return {entry["modid"] for entry in entries if isinstance(entry, dict) and isinstance(entry.get("modid"), str)}

    def _command(self, record: dict) -> list[str]:
        profile = self.profile
        args = [record["meta"]["directory"] if arg == "{preview}" else str(arg) for arg in profile.get("arguments", [])]
        java_args = list(profile.get("jvm_args", []))
        classpath = os.pathsep.join(map(str, profile.get("classpath", [])))
        maximum_heap = profile.get("max_heap", "2G")
        return [str(profile["java"]), *map(str, java_args), "-Xms16m", "-Xmx64m",
                "-Drml.preview.maxHeap=" + str(maximum_heap), "-cp", classpath,
                "rml.loader.agent.PreviewProcessMain", str(profile["main_class"]), *args]

    def _environment(self, record: dict, token: str, seed: int) -> dict:
        env = os.environ.copy()
        for name in ("RML_BRIDGE_TOKEN", "RML_SERVICE_TOKEN", "RML_PREVIEW_PROFILE", self.secret_env):
            if name:
                env.pop(name, None)
        ready = record["ready"]
        env.update(RML_PREVIEW_ID=record["meta"]["preview_id"], RML_PREVIEW_TOKEN=token,
                   RML_PREVIEW_READY=str(ready.absolute()), RML_PREVIEW_SEED=str(seed))
        return env

    async def _wait_ready(self, record: dict) -> None:
        process = record["process"]
        deadline = asyncio.get_running_loop().time() + _STARTUP_SECONDS
        ready_path = record["ready"]
        while asyncio.get_running_loop().time() < deadline:
            if process.returncode is not None:
                raise RuntimeError(self._error_text(RuntimeError(f"Preview exited with code {process.returncode}"), record))
            if ready_path.is_file():
                reject_links(ready_path)
                data = json.loads(await asyncio.to_thread(ready_path.read_text, encoding="utf-8"))
                if (isinstance(data, dict) and data.get("version") == 1
                        and data.get("preview_id") == record["meta"]["preview_id"]
                        and data.get("mode") == record["meta"]["mode"]):
                    world = Path(data["world"]).absolute()
                    reject_links(world)
                    world = world.resolve()
                    if not world.is_relative_to(Path(record["meta"]["directory"]).absolute().resolve()):
                        raise ValueError("Preview world must be inside its private directory")
                    record["endpoint"] = data["endpoint"]
                    if record["meta"]["mode"] == "server":
                        if data.get("port") != record.get("port"):
                            raise ValueError("Dedicated preview readiness port does not match its private server port")
                        record["join_address"] = f"127.0.0.1:{data['port']}"
                    return
            await asyncio.sleep(0.1)
        raise TimeoutError("Preview did not become ready within 180 seconds")

    async def _drain(self, record: dict) -> None:
        stream = record["process"].stdout
        while True:
            chunk = await stream.read(4096)
            if not chunk:
                await record["process"].wait()
                if record["meta"]["status"] == "running":
                    record["meta"]["status"] = "exited"
                    code = record["process"].returncode
                    record["meta"]["error"] = None if code == 0 else f"Preview process exited with code {code}"
                    self._save(record)
                return
            text = chunk.decode("utf-8", errors="replace")
            meta = record["meta"]
            meta["log_tail"] = (meta.get("log_tail", "") + text)[-_MAX_LOG:]
            self._save(record)

    @staticmethod
    def _error_text(exc: BaseException, record: dict) -> str:
        tail = record["meta"].get("log_tail", "")
        return (str(exc) or type(exc).__name__) + (("\n" + tail) if tail else "")

    async def _stop_process(self, record: dict) -> None:
        process = record.get("process")
        if process is None:
            self._release_port(record)
            return
        if process.returncode is None:
            if process.stdin is not None:
                process.stdin.close()
            try:
                await asyncio.wait_for(process.wait(), 5)
            except asyncio.TimeoutError:
                process.terminate()
                try:
                    await asyncio.wait_for(process.wait(), 5)
                except asyncio.TimeoutError:
                    process.kill()
                    await process.wait()
        if record.get("tasks"):
            await asyncio.gather(*record["tasks"], return_exceptions=True)
        if record.get("bridge") is not None:
            await record["bridge"].close()
            record["bridge"] = None
        self._release_port(record)

    def _release_port(self, record: dict) -> None:
        port = record.pop("port", None)
        if port is not None:
            with self._port_lock:
                self._ports.discard(port)

    async def _dispose(self, record: dict) -> bool:
        directory = Path(record["meta"]["directory"])
        try:
            reject_links(directory)
            if directory.exists():
                for current, directories, files in os.walk(directory, followlinks=False):
                    for name in directories + files:
                        reject_links(Path(current) / name)
                await asyncio.to_thread(shutil.rmtree, directory)
            return True
        except (OSError, ValueError):
            return False
