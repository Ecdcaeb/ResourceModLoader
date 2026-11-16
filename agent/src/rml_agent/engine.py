"""Real ADK Runner integration and the tools exposed to the configured model."""
from __future__ import annotations

import asyncio
from contextlib import aclosing
import os
from pathlib import Path
from typing import Callable
import uuid

from google.adk.agents import Agent
from google.adk.agents.run_config import RunConfig, StreamingMode
from google.adk.artifacts import FileArtifactService
from google.adk.events import Event
from google.adk.models.google_llm import Gemini
from google.adk.runners import Runner
from google.adk.sessions import DatabaseSessionService
from google.genai import types

from .bridge import Bridge
from .store import Store
from .workspace import Workspace, digest

APP_NAME = "rml_agent"
AGENT_NAME = "rml_operator"


def model_configuration(config: dict) -> tuple[dict | None, str | None]:
    model = config.get("model")
    if not isinstance(model, dict) or not model.get("name"):
        return None, "No model configured; set model.provider, model.name and model.api_key_env"
    if model.get("provider") not in ("gemini", "litellm"):
        return None, "model.provider must be gemini or litellm"
    if not isinstance(model.get("name"), str) or not model["name"].strip():
        return None, "model.name must be a nonempty string"
    key_env = model.get("api_key_env")
    if not isinstance(key_env, str) or not key_env or not os.environ.get(key_env):
        return None, "The configured model credential environment variable is missing"
    if key_env in ("RML_BRIDGE_TOKEN", "RML_SERVICE_TOKEN"):
        return None, "Model credentials must use their own environment variable"
    if model.get("base_url") is not None and not isinstance(model["base_url"], str):
        return None, "model.base_url must be a string"
    return model, None


class Engine:
    def __init__(self, config: dict, state_dir: Path, store: Store, bridge: Bridge):
        self.store = store
        self.bridge = bridge
        self.state_dir = state_dir
        self.compiler = Path(config.get("compiler", "rml-compiler.jar")).absolute()
        self.model_config, self.model_error = model_configuration(config)
        self.sessions = DatabaseSessionService(db_url="sqlite+aiosqlite:///" + (state_dir / "conversation.sqlite3").as_posix())
        self.artifacts = FileArtifactService(root_dir=state_dir / "artifacts")
        self.workspaces: dict[str, Workspace] = {}
        self.tasks: dict[str, asyncio.Task] = {}
        self.cancel_reasons: dict[str, str] = {}
        self.closing = False
        self._secrets = [value for value in (os.getenv("RML_BRIDGE_TOKEN"), os.getenv("RML_SERVICE_TOKEN"),
                         os.getenv(self.model_config["api_key_env"]) if self.model_config else None) if value]

    @property
    def model_configured(self) -> bool:
        return self.model_config is not None

    def clean(self, value):
        if isinstance(value, str):
            for secret in self._secrets:
                value = value.replace(secret, "[redacted]")
            return value
        if isinstance(value, dict):
            return {key: self.clean(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self.clean(item) for item in value]
        return value

    async def initialize(self) -> None:
        # Public read initializes ADK's SQLite tables without creating fake conversation.
        await self.sessions.list_sessions(app_name=APP_NAME)
        self.store.interrupt()

    def workspace(self, session_id: str) -> Workspace:
        if session_id not in self.workspaces:
            self.workspaces[session_id] = Workspace(self.state_dir / "workspaces", session_id)
        return self.workspaces[session_id]

    async def ensure_session(self, session: dict) -> None:
        self.workspace(session["id"])
        existing = await self.sessions.get_session(app_name=APP_NAME, user_id=session["principal"], session_id=session["id"])
        if existing is None:
            await self.sessions.create_session(app_name=APP_NAME, user_id=session["principal"], session_id=session["id"],
                                              state={"pack_id": self.workspace(session["id"]).pack_id})

    def start(self, session: dict, run: dict) -> None:
        if self.closing:
            raise RuntimeError("Service is shutting down")
        task = asyncio.create_task(self._run(session, run), name="rml-run-" + run["id"])
        self.tasks[run["id"]] = task
        task.add_done_callback(lambda finished: self.tasks.pop(run["id"], None))

    def cancel(self, session_id: str, run_id: str, reason: str = "cancelled") -> dict:
        run = self.store.run(session_id, run_id)
        if run is None:
            raise ValueError("Unknown run")
        task = self.tasks.get(run_id)
        if task and not task.done() and run_id not in self.cancel_reasons:
            self.cancel_reasons[run_id] = reason
            self.store.status(session_id, run_id, "cancelling")
            task.cancel()
            # A task cancelled before its first instruction never enters _run's finally.
            task.add_done_callback(lambda finished: self._finish_cancel(session_id, run_id, reason))
        return self.store.run(session_id, run_id)

    def _finish_cancel(self, session_id: str, run_id: str, reason: str) -> None:
        self.store.status(session_id, run_id, reason,
                          {"message": "Generation stopped; completed tool and native results remain in the journal."})
        self.cancel_reasons.pop(run_id, None)

    async def close(self) -> None:
        self.closing = True
        tasks = list(self.tasks.items())
        for run_id, task in tasks:
            if not task.done() and run_id not in self.cancel_reasons:
                # A second cancel must not interrupt child-process or bridge cleanup.
                self.cancel_reasons[run_id] = "interrupted"
                task.cancel()
        if tasks:
            await asyncio.gather(*(task for _, task in tasks), return_exceptions=True)
        try:
            self.store.interrupt()
        finally:
            try:
                await self.bridge.close()
            finally:
                await self.sessions.close()

    def _model(self):
        config = self.model_config
        if config is None:
            raise ValueError(self.model_error)
        key = os.environ[config["api_key_env"]]
        if config["provider"] == "gemini":
            return Gemini(model=config["name"], base_url=config.get("base_url"), client_kwargs={"api_key": key, "vertexai": False})
        from google.adk.models.lite_llm import LiteLlm
        options = {"api_key": key}
        if config.get("base_url"):
            options["api_base"] = config["base_url"]
        return LiteLlm(model=config["name"], **options)

    async def _repair_unfinished(self, session: dict, emit: Callable) -> None:
        conversation = await self.sessions.get_session(app_name=APP_NAME, user_id=session["principal"], session_id=session["id"])
        pending = {}
        for event in conversation.events:
            for call in event.get_function_calls():
                if call.id:
                    pending[(event.invocation_id, call.id)] = (event, call)
            for response in event.get_function_responses():
                pending.pop((event.invocation_id, response.id), None)
        for event, call in pending.values():
            message = "Previous execution ended without an ADK tool response. The outcome is unknown; no operation was replayed. Inspect runtime and workspace before attempting mutations."
            await self.sessions.append_event(conversation, Event(
                author=event.author, invocation_id=event.invocation_id, branch=event.branch,
                isolation_scope=event.isolation_scope, error_code="INVOCATION_ABORTED", error_message=message,
                content=types.Content(role="user", parts=[types.Part(function_response=types.FunctionResponse(
                    id=call.id, name=call.name, response={"error": message, "outcome": "unknown"}))])))
            emit("tool_result", {"name": call.name, "call_id": call.id, "recovered": True,
                                 "result": {"error": message, "outcome": "unknown"}})

    async def _run(self, session: dict, run: dict) -> None:
        session_id, run_id = session["id"], run["id"]
        def emit(kind, payload):
            self.store.emit(session_id, run_id, kind, self.clean(payload))
        runner = None
        model = None
        try:
            self.store.status(session_id, run_id, "running")
            await self.ensure_session(session)
            await self._repair_unfinished(session, emit)
            workspace = self.workspace(session_id)
            tools = self._tools(session, run_id, emit)
            model = self._model()
            agent = Agent(name=AGENT_NAME, model=model, instruction=(
                "You operate a real Minecraft 1.12.2 RML workspace. Use tools for all observations and mutations. "
                "Never claim a game result without the native bridge result. Source is an enhanced datapack: "
                "pack.mcmeta has pack.pack_format=3, pack.description, and rml.id=" + workspace.pack_id + ". "
                "Never change that id. Resources use data/<namespace>/<type>/<path>.json; functions use "
                "data/<namespace>/function/<path>.mcfunction (singular function). List/read files first; "
                "write and delete require the current SHA-256, or an empty digest for a new file. "
                "Compile creates an immutable build. Check the build to get a detached candidate and runtime revision, "
                "then apply with that expected revision only when requested. New native registry content requires "
                "install for next restart; never promise a live registry change. Native revisions are global, so "
                "inspect again after a revision conflict. Never blindly retry a native operation with unknown outcome. "
                "Cancellation/disconnection may leave already committed operations; inspect before retrying. "
                "Use run_scenario for installed test_instance definitions in inspect_runtime.scenarios; it waits for the "
                "native terminal result and cleanup. A started or running test is not a pass. Failed/cancelled tests "
                "and cleanup failures are not passes. Tests restore only declared blocks, block entities and world time; "
                "use an appropriate test area, since entity/inventory changes and external tasks are not rolled back. "
                "After interruption, inspect_runtime.scenario_runs and scenario_status recover retained results without rerunning. "
                "Use the operator's actual request to determine intended edits, commands and deployment."), tools=tools)
            runner = Runner(app_name=APP_NAME, agent=agent, session_service=self.sessions, artifact_service=self.artifacts)
            async with aclosing(runner.run_async(
                user_id=session["principal"], session_id=session_id,
                new_message=types.Content(role="user", parts=[types.Part.from_text(text=run["message"])]),
                run_config=RunConfig(streaming_mode=StreamingMode.SSE, max_llm_calls=100))) as stream:
                async for event in stream:
                    if event.error_code:
                        emit("model_error", {"code": event.error_code, "message": event.error_message})
                        if event.error_code != "INVOCATION_ABORTED":
                            raise RuntimeError(event.error_message or event.error_code)
                    for response in event.get_function_responses():
                        # ADK rejects malformed/unknown calls before our tool body is entered.
                        if response.response and response.response.get("error") and "error_type" not in response.response:
                            emit("tool_result", {"name": response.name, "call_id": response.id,
                                                 "result": response.response, "source": "adk"})
                    text = "".join(part.text for part in (event.content.parts if event.content and event.content.parts else [])
                                   if part.text and not part.thought)
                    if text:
                        emit("assistant", {"text": text, "partial": bool(event.partial),
                                           "event_id": event.id, "author": event.author})
            self.store.status(session_id, run_id, "completed")
        except asyncio.CancelledError:
            reason = self.cancel_reasons.get(run_id, "interrupted" if self.closing else "cancelled")
            self.store.status(session_id, run_id, reason,
                              {"message": "Generation stopped; completed tool results are preserved. Inspect unknown native outcomes before retrying."})
        except Exception as error:
            self.store.status(session_id, run_id, "failed", {"error": self.clean(str(error)), "error_type": type(error).__name__})
        finally:
            try:
                if runner is not None:
                    await runner.close()
            finally:
                if isinstance(model, Gemini):
                    await model.api_client.aio.aclose()
                    model.api_client.close()
                self.cancel_reasons.pop(run_id, None)

    def _tools(self, session: dict, run_id: str, emit: Callable) -> list:
        workspace = self.workspace(session["id"])

        async def perform(name, arguments, action):
            call_id = str(uuid.uuid4())
            emit("tool_start", {"name": name, "call_id": call_id, "arguments": arguments})
            try:
                async with workspace.lock:
                    result = await action()
                result = self.clean(result)
                emit("tool_result", {"name": name, "call_id": call_id, "result": result})
                return result
            except asyncio.CancelledError:
                emit("tool_result", {"name": name, "call_id": call_id, "result": {
                    "error": "Tool interrupted; consult saved build/native results for committed work", "outcome": "interrupted"}})
                raise
            except Exception as error:
                result = {"error": self.clean(str(error)), "error_type": type(error).__name__}
                emit("tool_result", {"name": name, "call_id": call_id, "result": result})
                return result

        async def native(operation, arguments):
            return await self.bridge.invoke(session["principal"], session["id"], operation, arguments, emit)

        async def inspect_runtime() -> dict:
            """Inspect the real game's runtime, active packs, session and current revision."""
            return await perform("inspect_runtime", {}, lambda: native("inspect", {}))

        async def list_files() -> dict:
            """List bounded workspace source files with their SHA-256 digests."""
            async def action():
                return {"files": workspace.list_files(), "pack_id": workspace.pack_id}
            return await perform("list_files", {}, action)

        async def read_file(path: str, encoding: str = "utf-8") -> dict:
            """Read a source file and its digest. Use base64 encoding for binary content."""
            async def action():
                return workspace.read_file(path, encoding)
            return await perform("read_file", {"path": path, "encoding": encoding}, action)

        async def write_file(path: str, content: str, expected_sha256: str, encoding: str = "utf-8") -> dict:
            """Atomically write source content. Existing files require their current digest; new files require an empty digest."""
            async def action():
                result = workspace.change(path, content, expected_sha256, encoding)
                emit("file_diff", result)
                return result
            return await perform("write_file", {"path": path, "expected_sha256": expected_sha256, "encoding": encoding}, action)

        async def delete_file(path: str, expected_sha256: str) -> dict:
            """Delete a source file only if its digest matches. The ownership manifest cannot be deleted."""
            async def action():
                result = workspace.change(path, None, expected_sha256)
                emit("file_diff", result)
                return result
            return await perform("delete_file", {"path": path, "expected_sha256": expected_sha256}, action)

        async def compile_project() -> dict:
            """Run the configured Java compiler on this source project; preserve actual output and immutable successful ZIPs."""
            async def action():
                result = await workspace.compile(self.compiler, emit, self.model_config["api_key_env"] if self.model_config else "")
                if result["returncode"] != 0:
                    return result
                data = workspace.build_path(result["id"]).read_bytes()
                filename = result["id"] + ".zip"
                async def publish():
                    version = await self.artifacts.save_artifact(app_name=APP_NAME, user_id=session["principal"],
                        session_id=session["id"], filename=filename,
                        artifact=types.Part.from_bytes(data=data, mime_type="application/zip"),
                        custom_metadata={"sha256": result["sha256"], "build_id": result["id"]})
                    result.update(artifact=filename, version=version)
                    self.store.save_build(session["id"], result)
                    emit("build", dict(result, phase="completed"))
                publication = asyncio.create_task(publish())
                try:
                    await asyncio.shield(publication)
                except asyncio.CancelledError:
                    # FileArtifactService writes in a thread; finish recording its committed version.
                    await publication
                    raise
                return result
            return await perform("compile_project", {}, action)

        def build_arguments(build_id):
            record = self.store.build(session["id"], build_id)
            path = workspace.build_path(build_id)
            if digest(path.read_bytes()) != record["sha256"]:
                raise ValueError("Immutable build digest mismatch")
            return {"archive": f"{session['id']}/builds/{build_id}.zip", "sha256": record["sha256"]}

        async def check_build(build_id: str) -> dict:
            """Import and check an owned immutable build as a detached native candidate. Does not apply it."""
            async def action():
                return await native("check", build_arguments(build_id))
            return await perform("check_build", {"build_id": build_id}, action)

        async def apply_candidate(candidate: str, revision: int) -> dict:
            """Apply this session's checked candidate against the expected global runtime revision."""
            arguments = {"candidate": candidate, "revision": revision}
            return await perform("apply_candidate", arguments, lambda: native("apply", arguments))

        async def deactivate_pack(runtime_session: str, revision: int) -> dict:
            """Deactivate only this workspace's pack, with expected runtime session and revision."""
            arguments = {"session": runtime_session, "revision": revision}
            return await perform("deactivate_pack", arguments, lambda: native("deactivate", arguments))

        async def game_command(command: str) -> dict:
            """Execute a real Minecraft command as the authenticated operator and return its actual messages/result."""
            return await perform("game_command", {"command": command}, lambda: native("command", {"command": command}))

        async def run_scenario(scenario: str, x: int, y: int, z: int) -> dict:
            """Run an installed world test at an explicit origin, await its assertions/cleanup, and cancel native work on interruption."""
            scenario_run = str(uuid.uuid4())
            arguments = {"run_id": scenario_run, "scenario": scenario, "x": x, "y": y, "z": z}
            async def action():
                try:
                    response = await native("scenario_start", arguments)
                    while response.get("ok") and response["result"]["status"] == "running":
                        emit("scenario", response["result"])
                        await asyncio.sleep(1)
                        response = await native("scenario_status", {"run_id": scenario_run})
                    if response.get("ok"):
                        emit("scenario", response["result"])
                    return dict(response, scenario_run_id=scenario_run)
                except asyncio.CancelledError:
                    # The id is known before start dispatch, including a lost start response.
                    cancellation = await native("scenario_cancel", {"run_id": scenario_run})
                    emit("scenario_cancel", {"run_id": scenario_run, "response": cancellation})
                    raise
            return await perform("run_scenario", arguments, action)

        async def scenario_status(run_id: str) -> dict:
            """Read this session's retained native scenario result without starting another test."""
            arguments = {"run_id": str(uuid.UUID(run_id))}
            return await perform("scenario_status", arguments, lambda: native("scenario_status", arguments))

        async def cancel_scenario(run_id: str) -> dict:
            """Cancel an owned native scenario and return its actual cleanup result; completed results remain unchanged."""
            arguments = {"run_id": str(uuid.UUID(run_id))}
            return await perform("cancel_scenario", arguments, lambda: native("scenario_cancel", arguments))

        async def install_build(build_id: str) -> dict:
            """Install an owned immutable build for the next JVM restart; this does not activate native registry definitions live."""
            async def action():
                return await native("install", build_arguments(build_id))
            return await perform("install_build", {"build_id": build_id}, action)

        return [inspect_runtime, list_files, read_file, write_file, delete_file, compile_project,
                check_build, apply_candidate, deactivate_pack, game_command, install_build,
                run_scenario, scenario_status, cancel_scenario]
