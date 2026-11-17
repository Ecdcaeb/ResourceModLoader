"""Loopback HTTP service for durable operator sessions and detached ADK runs."""
from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
import ipaddress
import json
import os
from pathlib import Path
import secrets
import socket
from typing import Callable

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response, StreamingResponse
import uvicorn

from .bridge import Bridge
from .engine import APP_NAME, Engine
from .store import Conflict, Store, canonical_uuid, principal_id
from .workspace import atomic_write, digest, reject_links

MAX_REQUEST_BYTES = 1024 * 1024


async def json_body(request: Request) -> dict:
    content = bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content) > MAX_REQUEST_BYTES:
            raise HTTPException(413, "Request body exceeds size limit")
    try:
        result = json.loads(content)
    except (ValueError, UnicodeError):
        raise HTTPException(400, "Expected a JSON object") from None
    if not isinstance(result, dict):
        raise HTTPException(400, "Expected a JSON object")
    return result


def require_token(request: Request, expected: str) -> None:
    supplied = request.headers.get("authorization", "")
    if not expected or not secrets.compare_digest(supplied.encode(), ("Bearer " + expected).encode()):
        raise HTTPException(401, "Invalid bearer token")


def create_app(config: dict, state_dir: Path, bridge_url: str, instance_id: str,
               shutdown: Callable[[], None] | None = None) -> FastAPI:
    """Build the service. The caller owns ASGI lifespan and listener startup."""
    instance_id = canonical_uuid(instance_id)
    state_dir = state_dir.absolute()
    reject_links(state_dir)
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    admin_token = os.environ.get("RML_SERVICE_TOKEN", "")
    bridge_token = os.environ.get("RML_BRIDGE_TOKEN", "")
    if not admin_token or not bridge_token:
        raise ValueError("RML_SERVICE_TOKEN and RML_BRIDGE_TOKEN are required")
    for name in ("journal.sqlite3", "conversation.sqlite3", "artifacts", "workspaces"):
        reject_links(state_dir / name)
    store = Store(state_dir / "journal.sqlite3", instance_id)
    engine = Engine(config, state_dir, store, Bridge(bridge_url, bridge_token))
    enrollment = asyncio.Lock()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        try:
            await engine.initialize()
            app.state.ready = True
            yield
        finally:
            app.state.ready = False
            try:
                await engine.close()
            finally:
                store.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.engine = engine
    app.state.store = store
    app.state.ready = False

    def session_auth(request: Request, session_id: str) -> dict:
        try:
            canonical_uuid(session_id)
        except (ValueError, AttributeError):
            raise HTTPException(404, "Unknown session") from None
        session = store.session(session_id)
        if session is None:
            raise HTTPException(401, "Invalid bearer token")
        require_token(request, session["token"])
        return session

    def public_session(session: dict, include_token: bool = False) -> dict:
        workspace = engine.workspace(session["id"])
        result = {"id": session["id"], "workspace": str(workspace.source), "pack_id": workspace.pack_id,
                  "principal": session["principal"], "model_configured": engine.model_configured,
                  "model_error": engine.model_error, "active_run": store.active(session["id"]),
                  "last_seq": store.last_seq(session["id"]), "previews": engine.previews.summaries(session["id"])}
        if include_token:
            result["token"] = session["token"]
        return result

    @app.get("/health")
    async def health():
        return {"ready": app.state.ready and not engine.closing, "instance": instance_id,
                "model_configured": engine.model_configured, "model_error": engine.model_error}

    @app.post("/v1/shutdown")
    async def stop(request: Request):
        require_token(request, admin_token)
        if shutdown is None:
            raise HTTPException(503, "This service host does not expose shutdown")
        # Close SSE streams before uvicorn waits for open connections to drain.
        engine.closing = True
        asyncio.get_running_loop().call_soon(shutdown)
        return {"status": "shutting_down"}

    @app.post("/v1/sessions")
    async def open_session(request: Request):
        require_token(request, admin_token)
        if engine.closing:
            raise HTTPException(503, "Service is shutting down")
        body = await json_body(request)
        try:
            principal = principal_id(body.get("principal"))
        except (ValueError, TypeError, AttributeError):
            raise HTTPException(400, "principal must be console or a canonical player UUID") from None
        async with enrollment:
            session = store.open_session(principal)
            await engine.ensure_session(session)
        return public_session(session, include_token=True)

    @app.get("/v1/sessions/{session_id}")
    async def get_session(session_id: str, request: Request):
        return public_session(session_auth(request, session_id))

    @app.post("/v1/sessions/{session_id}/runs")
    async def submit(session_id: str, request: Request):
        session = session_auth(request, session_id)
        if engine.closing:
            raise HTTPException(503, "Service is shutting down")
        if not engine.model_configured:
            raise HTTPException(503, {"code": "model_not_configured", "message": engine.model_error})
        body = await json_body(request)
        try:
            request_id = canonical_uuid(body.get("request_id"))
        except (ValueError, AttributeError, TypeError):
            raise HTTPException(400, "request_id must be a canonical UUID") from None
        message = body.get("message")
        if not isinstance(message, str) or not message.strip() or len(message) > 65536:
            raise HTTPException(400, "message must contain 1 to 65536 characters")
        try:
            run, created = store.submit(session_id, request_id, message)
        except Conflict as error:
            raise HTTPException(409, str(error)) from None
        if created:
            engine.start(session, run)
        return {"run_id": run["id"], "status": run["status"]}

    @app.post("/v1/sessions/{session_id}/runs/{run_id}/cancel")
    async def cancel(session_id: str, run_id: str, request: Request):
        session_auth(request, session_id)
        try:
            canonical_uuid(run_id)
            run = engine.cancel(session_id, run_id)
        except (ValueError, AttributeError):
            raise HTTPException(404, "Unknown run") from None
        return {"run_id": run["id"], "status": run["status"]}

    @app.get("/v1/sessions/{session_id}/events")
    async def events(session_id: str, request: Request):
        session_auth(request, session_id)
        try:
            after = int(request.query_params.get("after", request.headers.get("last-event-id", "0")))
            if after < 0 or after > 9223372036854775807:
                raise ValueError()
        except ValueError:
            raise HTTPException(400, "after must be a nonnegative SQLite sequence number") from None

        async def stream():
            cursor = after
            heartbeat = asyncio.get_running_loop().time()
            while not engine.closing:
                if await request.is_disconnected():
                    return
                batch = store.events(session_id, cursor)
                for event in batch:
                    cursor = event["seq"]
                    yield f"id: {cursor}\ndata: {json.dumps(event, ensure_ascii=False)}\n\n"
                if batch:
                    continue
                if asyncio.get_running_loop().time() - heartbeat >= 15:
                    yield ": heartbeat\n\n"
                    heartbeat = asyncio.get_running_loop().time()
                await asyncio.sleep(0.2)
        return StreamingResponse(stream(), media_type="text/event-stream", headers={
            "Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @app.get("/v1/sessions/{session_id}/artifacts")
    async def artifacts(session_id: str, request: Request):
        session_auth(request, session_id)
        return {"artifacts": [{"name": item["artifact"], "version": item["version"],
                                "build_id": item["id"], "sha256": item["sha256"], "size": item["size"]}
                               for item in store.builds(session_id)]}

    @app.get("/v1/sessions/{session_id}/artifacts/{name}")
    async def artifact(session_id: str, name: str, request: Request, version: int | None = None):
        session = session_auth(request, session_id)
        try:
            if not name.endswith(".zip"):
                raise ValueError()
            record = store.build(session_id, name[:-4])
            if name != record["artifact"] or version is not None and version != record["version"]:
                raise ValueError()
        except (ValueError, AttributeError):
            raise HTTPException(404, "Unknown artifact or version") from None
        part = await engine.artifacts.load_artifact(app_name=APP_NAME, user_id=session["principal"],
                                                   session_id=session_id, filename=name, version=record["version"])
        if part is None or part.inline_data is None:
            raise HTTPException(404, "Artifact payload is missing")
        if part.inline_data.data is None or digest(part.inline_data.data) != record["sha256"]:
            raise HTTPException(409, "Stored artifact no longer matches its immutable build digest")
        return Response(content=part.inline_data.data, media_type=part.inline_data.mime_type,
                        headers={"ETag": '"' + record["sha256"] + '"',
                                 "Content-Disposition": 'attachment; filename="' + name + '"'})

    return app


def serve(config_path: Path, state_dir: Path, bridge_url: str, instance_id: str, ready_file: Path) -> None:
    """Start uvicorn and publish readiness only after SQLite and the listener are ready."""
    config = json.loads(Path(config_path).read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise ValueError("Configuration must be a JSON object")
    host = config.get("service_host", "127.0.0.1")
    if host == "localhost":
        host = "127.0.0.1"
    if not isinstance(host, str) or not ipaddress.ip_address(host).is_loopback:
        raise ValueError("service_host must be a loopback address")
    port = config.get("service_port", 0)
    if isinstance(port, bool) or not isinstance(port, int) or not 0 <= port <= 65535:
        raise ValueError("service_port must be between 0 and 65535")
    state_dir = Path(state_dir).absolute()
    reject_links(state_dir)
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    lock_path = state_dir / "service.lock"
    reject_links(lock_path)
    with lock_path.open("a+b") as lock:
        # A second process must not mark the live process's runs interrupted.
        if os.name == "nt":
            import msvcrt
            if lock.seek(0, os.SEEK_END) == 0:
                lock.write(b"0")
                lock.flush()
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        ready_file = Path(ready_file).absolute()
        reject_links(ready_file)
        ready_file.unlink(missing_ok=True)
        listener = socket.socket(socket.AF_INET6 if ":" in host else socket.AF_INET, socket.SOCK_STREAM)
        try:
            if os.name == "nt":
                listener.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            listener.bind((host, port))
            listener.setblocking(False)
            bound_port = listener.getsockname()[1]
            endpoint = f"http://[{host}]:{bound_port}" if ":" in host else f"http://{host}:{bound_port}"

            def shutdown():
                server.should_exit = True

            app = create_app(config, state_dir, bridge_url, instance_id, shutdown)

            class ReadyServer(uvicorn.Server):
                async def startup(self, sockets=None):
                    await super().startup(sockets=sockets)
                    if self.started and app.state.ready:
                        atomic_write(ready_file, json.dumps({"endpoint": endpoint, "instance": instance_id,
                                                            "pid": os.getpid()}).encode("utf-8"))

                async def shutdown(self, sockets=None):
                    app.state.engine.closing = True
                    await super().shutdown(sockets=sockets)

            server = ReadyServer(uvicorn.Config(app, host=host, port=bound_port, access_log=False,
                                                timeout_graceful_shutdown=2, log_level="warning"))
            server.run(sockets=[listener])
        finally:
            listener.close()
            ready_file.unlink(missing_ok=True)
