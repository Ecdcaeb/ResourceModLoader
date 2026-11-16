"""Owned source projects and cancellable invocations of the real Java compiler."""
from __future__ import annotations

import asyncio
import base64
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import uuid

from .store import canonical_uuid

MAX_FILE_BYTES = 2 * 1024 * 1024
MAX_PROJECT_BYTES = 32 * 1024 * 1024
MAX_FILES = 4096
MAX_BUILD_BYTES = 64 * 1024 * 1024
_RESERVED = re.compile(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)", re.I)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_relative(name: str) -> tuple[str, ...]:
    if not isinstance(name, str) or not name or len(name) > 512 or "\\" in name:
        raise ValueError("Use a bounded relative path with forward slashes")
    parts = name.split("/")
    if any(not p or p in (".", "..") or p[-1:] in (".", " ")
           or any(ord(c) < 32 or c in ':*?"<>|' for c in p) or _RESERVED.match(p)
           for p in parts):
        raise ValueError("Unsafe workspace path")
    return tuple(parts)


def reject_links(path: Path) -> None:
    """Check every existing ancestor, including Windows junction/reparse points."""
    for part in [*reversed(path.absolute().parents), path.absolute()]:
        try:
            info = part.lstat()
        except FileNotFoundError:
            continue
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise ValueError("Symlinks and reparse points are not permitted")
        if stat.S_ISREG(info.st_mode) and info.st_nlink != 1:
            raise ValueError("Hard-linked files are not permitted")
        if not stat.S_ISREG(info.st_mode) and not stat.S_ISDIR(info.st_mode):
            raise ValueError("Only regular files and directories are permitted")


def atomic_write(path: Path, data: bytes) -> None:
    reject_links(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".rml-write-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        reject_links(path)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


async def run_process(argv: list[str], environment: dict[str, str], output) -> dict:
    """Drain a real child process and reap it even when cancellation races with spawn."""
    spawn = asyncio.create_task(asyncio.create_subprocess_exec(
        *argv, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE, env=environment))
    process = None
    drains = []
    captured = {"stdout": [], "stderr": []}
    counts = {"stdout": 0, "stderr": 0}

    async def stop():
        if process is None:
            return
        if process.returncode is None:
            try:
                process.terminate()
            except ProcessLookupError:
                pass
        try:
            # Read buffered pipes too: waiting on exit alone can hang on a full pipe.
            await asyncio.wait_for(process.communicate(), 3)
        except asyncio.TimeoutError:
            process.kill()
            await process.communicate()

    async def drain(stream, channel):
        import codecs
        decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
        while chunk := await stream.read(4096):
            text = decoder.decode(chunk)
            output(channel, text)
            if counts[channel] < MAX_FILE_BYTES:
                captured[channel].append(text)
                counts[channel] += len(chunk)
        tail = decoder.decode(b"", final=True)
        if tail:
            output(channel, tail)
            captured[channel].append(tail)

    try:
        process = await asyncio.shield(spawn)
        drains = [asyncio.create_task(drain(process.stdout, "stdout")),
                  asyncio.create_task(drain(process.stderr, "stderr"))]
        returncode = (await asyncio.gather(process.wait(), *drains))[0]
        return {"returncode": returncode, "stdout": "".join(captured["stdout"]),
                "stderr": "".join(captured["stderr"])}
    except BaseException:
        if process is None:
            try:
                process = await spawn
            except Exception:
                pass
        for task in drains:
            if not task.done():
                task.cancel()
        await asyncio.gather(*drains, return_exceptions=True)
        await stop()
        raise
    finally:
        for task in drains:
            if not task.done():
                task.cancel()
        await asyncio.gather(*drains, return_exceptions=True)


class Workspace:
    def __init__(self, root: Path, session_id: str):
        self.session_id = canonical_uuid(session_id)
        self.pack_id = "rml_agent_" + session_id.replace("-", "")
        self.root = root.absolute() / session_id
        self.source = self.root / "source"
        self.build_dir = self.root / "builds"
        reject_links(self.root)
        self.source.mkdir(parents=True, exist_ok=True)
        self.build_dir.mkdir(parents=True, exist_ok=True)
        self.lock = asyncio.Lock()
        metadata = self.path("pack.mcmeta")
        if not metadata.exists():
            # A metadata-only project is explicitly supported by ProjectReader.
            atomic_write(metadata, (json.dumps({
                "pack": {"pack_format": 3, "description": "RML agent workspace"},
                "rml": {"id": self.pack_id, "name": "Agent workspace", "version": "1.0",
                        "language_version": 1, "minecraft": "1.12.2"},
            }, indent=2) + "\n").encode())
        if metadata.stat().st_size > MAX_FILE_BYTES:
            raise ValueError("Ownership manifest exceeds file size limit")
        self.validate_metadata(metadata.read_bytes())

    def path(self, name: str) -> Path:
        path = self.source.joinpath(*safe_relative(name))
        reject_links(path)
        resolved = path.resolve()
        source = self.source.resolve()
        if not resolved.is_relative_to(source):
            raise ValueError("Workspace path escapes source root")
        if resolved.relative_to(source).as_posix().casefold() != name.casefold():
            raise ValueError("Filesystem aliases are not permitted")
        return path

    def validate_metadata(self, data: bytes) -> None:
        def unique_object(pairs):
            obj = {}
            for key, value in pairs:
                if key in obj:
                    raise ValueError("Duplicate metadata key")
                obj[key] = value
            return obj
        try:
            metadata = json.loads(data, object_pairs_hook=unique_object)
            if metadata["rml"]["id"] != self.pack_id:
                raise ValueError("Workspace pack ownership cannot change")
            if metadata["rml"].get("minecraft", "1.12.2") != "1.12.2":
                raise ValueError("Workspace target must be Minecraft 1.12.2")
        except (KeyError, TypeError, UnicodeError, json.JSONDecodeError) as error:
            raise ValueError("Invalid pack.mcmeta") from error

    def list_files(self) -> list[dict]:
        reject_links(self.source)
        result = []
        total = 0
        for directory, directories, files in os.walk(self.source, followlinks=False):
            for name in directories + files:
                reject_links(Path(directory) / name)
            for name in files:
                path = Path(directory) / name
                relative = path.relative_to(self.source).as_posix()
                safe_relative(relative)
                size = path.stat().st_size
                total += size
                if size > MAX_FILE_BYTES or total > MAX_PROJECT_BYTES or len(result) >= MAX_FILES:
                    raise ValueError("Workspace exceeds file or project size limits")
                result.append({"path": relative, "size": size, "sha256": digest(path.read_bytes())})
        return sorted(result, key=lambda item: item["path"])

    def read_file(self, name: str, encoding: str = "utf-8") -> dict:
        path = self.path(name)
        if path.stat().st_size > MAX_FILE_BYTES:
            raise ValueError("File exceeds size limit")
        data = path.read_bytes()
        if encoding not in ("utf-8", "base64"):
            raise ValueError("encoding must be utf-8 or base64")
        return {"path": name, "sha256": digest(data), "encoding": encoding,
                "content": base64.b64encode(data).decode("ascii") if encoding == "base64" else data.decode("utf-8")}

    def change(self, name: str, content: str | None, expected_sha256: str, encoding: str = "utf-8") -> dict:
        path = self.path(name)
        exists = path.exists()
        before = path.read_bytes() if exists and path.stat().st_size <= MAX_FILE_BYTES else b""
        if exists and (not path.is_file() or path.stat().st_size > MAX_FILE_BYTES):
            raise ValueError("Target is not a bounded regular file")
        if (digest(before) if exists else "") != expected_sha256:
            raise ValueError("Digest conflict; read the current file before changing it (new files require an empty digest)")
        if content is None:
            if name.casefold() == "pack.mcmeta":
                raise ValueError("The ownership manifest cannot be deleted")
            if not exists:
                raise ValueError("Cannot delete a missing file")
            after = b""
            path.unlink()
        else:
            if encoding not in ("utf-8", "base64"):
                raise ValueError("encoding must be utf-8 or base64")
            if len(content) > MAX_FILE_BYTES * 2:
                raise ValueError("File exceeds size limit")
            after = base64.b64decode(content, validate=True) if encoding == "base64" else content.encode("utf-8")
            if len(after) > MAX_FILE_BYTES:
                raise ValueError("File exceeds size limit")
            files = self.list_files()
            if sum(item["size"] for item in files) - len(before) + len(after) > MAX_PROJECT_BYTES or (not exists and len(files) >= MAX_FILES):
                raise ValueError("Workspace exceeds project limits")
            if name.casefold() == "pack.mcmeta":
                if name != "pack.mcmeta":
                    raise ValueError("Use the canonical pack.mcmeta filename")
                self.validate_metadata(after)
            atomic_write(path, after)
        try:
            difference = "".join(difflib.unified_diff(before.decode("utf-8").splitlines(keepends=True),
                                                      after.decode("utf-8").splitlines(keepends=True),
                                                      fromfile=name, tofile=name))
            binary = False
        except UnicodeDecodeError:
            difference, binary = "Binary content changed", True
        return {"path": name, "before_sha256": digest(before) if exists else None,
                "sha256": digest(after) if content is not None else None,
                "deleted": content is None, "binary": binary, "diff": difference}

    def build_path(self, build_id: str) -> Path:
        path = self.build_dir / (canonical_uuid(build_id) + ".zip")
        reject_links(path)
        return path

    async def compile(self, compiler: Path, emit, secret_env: str = "") -> dict:
        self.list_files()
        self.validate_metadata(self.path("pack.mcmeta").read_bytes())
        if not compiler.is_file():
            raise ValueError("Configured compiler JAR does not exist")
        build_id = str(uuid.uuid4())
        destination = self.build_path(build_id)
        temporary = self.build_dir / (build_id + ".partial.zip")
        environment = os.environ.copy()
        for key in ("RML_BRIDGE_TOKEN", "RML_SERVICE_TOKEN", secret_env):
            environment.pop(key, None)
        try:
            result = await run_process(
                [os.environ.get("RML_JAVA", "java"), "-jar", str(compiler), "compile", str(self.source), str(temporary)],
                environment, lambda channel, text: emit("build", {
                    "id": build_id, "phase": "output", "stream": channel, "text": text}))
            result["id"] = build_id
            returncode = result["returncode"]
            if returncode:
                emit("build", dict(result, phase="failed"))
                return result
            reject_links(temporary)
            if not temporary.is_file() or temporary.stat().st_size > MAX_BUILD_BYTES:
                raise ValueError("Compiler did not produce a bounded build archive")
            if destination.exists():
                raise ValueError("Build identifier collision")
            data = temporary.read_bytes()
            # Hard-link publication fails if the immutable destination already exists.
            os.link(temporary, destination)
            temporary.unlink()
            result.update(sha256=digest(data), size=len(data), archive=f"{self.session_id}/builds/{build_id}.zip")
            return result
        except asyncio.CancelledError:
            emit("build", {"id": build_id, "phase": "cancelled", "message": "Compiler process stopped"})
            raise
        finally:
            temporary.unlink(missing_ok=True)
