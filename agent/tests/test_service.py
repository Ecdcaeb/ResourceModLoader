import asyncio
import json
import uuid

import httpx
import pytest
from starlette.requests import Request

from rml_agent.service import create_app


@pytest.fixture
def credentials(monkeypatch):
    monkeypatch.setenv("RML_SERVICE_TOKEN", "test-admin-credential")
    monkeypatch.setenv("RML_BRIDGE_TOKEN", "test-bridge-credential")
    return {"Authorization": "Bearer test-admin-credential"}


def make_app(tmp_path):
    return create_app({}, tmp_path, "http://127.0.0.1:9", str(uuid.uuid4()))


async def enroll(client, credentials, principal="console"):
    response = await client.post("/v1/sessions", headers=credentials, json={"principal": principal})
    assert response.status_code == 200
    session = response.json()
    return session, {"Authorization": "Bearer " + session["token"]}


@pytest.mark.asyncio
async def test_missing_model_is_healthy_explicit_and_sessions_are_isolated(tmp_path, credentials):
    app = make_app(tmp_path)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://service") as client:
            health = (await client.get("/health")).json()
            assert health["ready"] and not health["model_configured"]
            assert (await client.post("/v1/sessions", json={"principal": "console"})).status_code == 401
            one, auth_one = await enroll(client, credentials)
            two, auth_two = await enroll(client, credentials, str(uuid.uuid4()))
            assert one["token"] != two["token"]
            assert (await client.get(f"/v1/sessions/{one['id']}", headers=auth_two)).status_code == 401
            assert (await client.get(f"/v1/sessions/{one['id']}", headers=credentials)).status_code == 401
            response = await client.get(f"/v1/sessions/{one['id']}", headers=auth_one)
            assert response.status_code == 200 and response.json()["last_seq"] == 0
            assert "token" not in response.json()
            assert "test-admin-credential" not in response.text and "test-bridge-credential" not in response.text
            missing = await client.post(f"/v1/sessions/{one['id']}/runs", headers=auth_one,
                                        json={"request_id": str(uuid.uuid4()), "message": "hello"})
            assert missing.status_code == 503
            assert missing.json()["detail"]["code"] == "model_not_configured"
            assert app.state.store.active(one["id"]) is None
            assert (await client.get(f"/v1/sessions/{one['id']}/artifacts", headers=auth_two)).status_code == 401
            assert (await client.get(f"/v1/sessions/{one['id']}/artifacts/not-owned.zip", headers=auth_one)).status_code == 404


@pytest.mark.asyncio
async def test_session_token_and_adk_conversation_survive_service_restart(tmp_path, credentials):
    from google.adk.events import Event
    from google.genai import types
    from rml_agent.engine import APP_NAME
    instance = str(uuid.uuid4())
    app = create_app({}, tmp_path, "http://127.0.0.1:9", instance)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://service") as client:
            saved, auth = await enroll(client, credentials)
            conversation = await app.state.engine.sessions.get_session(app_name=APP_NAME, user_id="console", session_id=saved["id"])
            await app.state.engine.sessions.append_event(conversation, Event(author="user", invocation_id=str(uuid.uuid4()),
                content=types.Content(role="user", parts=[types.Part.from_text(text="A persisted user message")])) )
    restarted = create_app({}, tmp_path, "http://127.0.0.1:9", instance)
    async with restarted.router.lifespan_context(restarted):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=restarted), base_url="http://service") as client:
            restored, _ = await enroll(client, credentials)
            assert restored["id"] == saved["id"] and restored["token"] == saved["token"]
            assert (await client.get(f"/v1/sessions/{saved['id']}", headers=auth)).status_code == 200
            conversation = await restarted.state.engine.sessions.get_session(app_name=APP_NAME, user_id="console", session_id=saved["id"])
            assert conversation.events[-1].content.parts[0].text == "A persisted user message"


@pytest.mark.asyncio
async def test_sse_replays_durable_events_and_detaching_does_not_cancel_run(tmp_path, credentials):
    app = make_app(tmp_path)
    async with app.router.lifespan_context(app):
        store = app.state.store
        session = store.open_session("console")
        other = store.open_session(str(uuid.uuid4()))
        run, _ = store.submit(session["id"], str(uuid.uuid4()), "waiting for operator")
        seq = store.emit(session["id"], run["id"], "run_started", {})
        store.emit(other["id"], None, "assistant", {"text": "other principal"})
        last = store.emit(session["id"], run["id"], "build", {"text": "real journal event"})
        endpoint = next(route.endpoint for route in app.routes if route.path == "/v1/sessions/{session_id}/events")
        async def receive():
            return {"type": "http.request", "body": b"", "more_body": False}
        def request(after):
            return Request({"type": "http", "method": "GET", "path": "/", "query_string": f"after={after}".encode(),
                            "headers": [(b"authorization", ("Bearer " + session["token"]).encode())]}, receive)
        response = await endpoint(session["id"], request(seq))
        frame = await anext(response.body_iterator)
        assert frame.startswith(f"id: {last}\n")
        assert json.loads(frame.split("data: ", 1)[1])["payload"]["text"] == "real journal event"
        await response.body_iterator.aclose()
        assert store.active(session["id"])["id"] == run["id"]
        next_seq = store.emit(session["id"], run["id"], "assistant", {"text": "after detach"})
        resumed = await endpoint(session["id"], request(last))
        assert (await anext(resumed.body_iterator)).startswith(f"id: {next_seq}\n")
        await resumed.body_iterator.aclose()
        assert store.run(session["id"], run["id"])["status"] == "queued"


@pytest.mark.asyncio
async def test_cancel_before_runner_starts_is_durable_and_idempotent(tmp_path, credentials):
    app = make_app(tmp_path)
    async with app.router.lifespan_context(app):
        engine, store = app.state.engine, app.state.store
        session = store.open_session("console")
        run, _ = store.submit(session["id"], str(uuid.uuid4()), "cancel before model invocation")
        engine.start(session, run)
        task = engine.tasks[run["id"]]
        engine.cancel(session["id"], run["id"])
        await asyncio.gather(task, return_exceptions=True)
        await asyncio.sleep(0)
        assert store.run(session["id"], run["id"])["status"] == "cancelled"
        assert engine.cancel(session["id"], run["id"])["status"] == "cancelled"
        assert [event["type"] for event in store.events(session["id"], 0)] == ["user", "run_cancelled"]


@pytest.mark.asyncio
async def test_shutdown_requires_management_auth_and_closes_streams(tmp_path, credentials):
    stopped = asyncio.Event()
    app = create_app({}, tmp_path, "http://127.0.0.1:9", str(uuid.uuid4()), stopped.set)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://service") as client:
            _, session_auth = await enroll(client, credentials)
            assert (await client.post("/v1/shutdown", headers=session_auth)).status_code == 401
            assert (await client.post("/v1/shutdown", headers=credentials)).status_code == 200
            await asyncio.wait_for(stopped.wait(), 1)
            assert app.state.engine.closing
            assert not (await client.get("/health")).json()["ready"]


@pytest.mark.asyncio
async def test_actual_file_artifact_service_is_persistent_and_session_scoped(tmp_path, credentials):
    from google.genai import types
    from google.adk.artifacts import FileArtifactService
    from rml_agent.engine import APP_NAME
    artifacts = FileArtifactService(root_dir=tmp_path / "artifacts")
    session, other = str(uuid.uuid4()), str(uuid.uuid4())
    version = await artifacts.save_artifact(app_name=APP_NAME, user_id="console", session_id=session,
        filename="storage-test.bin", artifact=types.Part.from_bytes(data=b"persisted artifact bytes", mime_type="application/octet-stream"))
    reopened = FileArtifactService(root_dir=tmp_path / "artifacts")
    loaded = await reopened.load_artifact(app_name=APP_NAME, user_id="console", session_id=session,
                                         filename="storage-test.bin", version=version)
    assert loaded.inline_data.data == b"persisted artifact bytes"
    assert await reopened.load_artifact(app_name=APP_NAME, user_id="console", session_id=other,
                                        filename="storage-test.bin", version=version) is None


@pytest.mark.asyncio
async def test_unanswered_adk_call_is_sealed_as_unknown_without_replay(tmp_path, credentials):
    from google.adk.events import Event
    from google.genai import types
    from rml_agent.engine import AGENT_NAME, APP_NAME
    app = make_app(tmp_path)
    async with app.router.lifespan_context(app):
        engine = app.state.engine
        session = app.state.store.open_session("console")
        await engine.ensure_session(session)
        conversation = await engine.sessions.get_session(app_name=APP_NAME, user_id="console", session_id=session["id"])
        call_id = str(uuid.uuid4())
        # Seed a crash boundary in the actual ADK database; no model or game execution is claimed.
        await engine.sessions.append_event(conversation, Event(
            author=AGENT_NAME, invocation_id=str(uuid.uuid4()),
            content=types.Content(role="model", parts=[types.Part(function_call=types.FunctionCall(
                id=call_id, name="game_command", args={"command": "say interrupted"}))])))
        events = []
        await engine._repair_unfinished(session, lambda *event: events.append(event))
        await engine._repair_unfinished(session, lambda *event: events.append(event))
        assert len(events) == 1
        assert events[0][1]["result"]["outcome"] == "unknown"
        restored = await engine.sessions.get_session(app_name=APP_NAME, user_id="console", session_id=session["id"])
        answer = restored.events[-1].get_function_responses()[0]
        assert answer.id == call_id and answer.response["outcome"] == "unknown"
        assert "no operation was replayed" in answer.response["error"]
