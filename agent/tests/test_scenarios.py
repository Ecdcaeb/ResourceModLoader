"""Cancellation contracts at the transport boundary; native world behavior is verified separately."""
import asyncio
import json
import uuid

import httpx
import pytest

from rml_agent.service import create_app


@pytest.mark.asyncio
@pytest.mark.parametrize("cancel_during_start", [False, True])
async def test_scenario_cancel_reaps_native_run_even_before_start_reply(tmp_path, monkeypatch, cancel_during_start):
    monkeypatch.setenv("RML_SERVICE_TOKEN", "scenario-service-test")
    monkeypatch.setenv("RML_BRIDGE_TOKEN", "scenario-bridge-test")
    app = create_app({}, tmp_path, "http://127.0.0.1:9", str(uuid.uuid4()))
    async with app.router.lifespan_context(app):
        engine, store = app.state.engine, app.state.store
        session = store.open_session("console")
        started, reply_released, observed = asyncio.Event(), asyncio.Event(), asyncio.Event()
        native = {"active": False, "starts": 0, "cleanups": 0}
        events = []

        def emit(kind, payload):
            events.append((kind, payload))
            if kind == "scenario":
                observed.set()

        async def dispatch(request):
            payload = json.loads(request.content)
            if request.url.path == "/cancel":
                reply_released.set()
                return httpx.Response(200, json={"ok": True, "result": {"status": "running", "cancelled": False}})
            operation, arguments = payload["operation"], payload["arguments"]
            if operation == "scenario_start":
                native.update(active=True, run_id=arguments["run_id"])
                native["starts"] += 1
                started.set()
                if cancel_during_start:
                    await reply_released.wait()
                return httpx.Response(200, json={"ok": True, "result": {"run_id": native["run_id"], "status": "running"}})
            if operation == "scenario_cancel":
                assert arguments["run_id"] == native["run_id"]
                assert payload["session_id"] == session["id"]
                native["active"] = False
                native["cleanups"] += 1
                return httpx.Response(200, json={"ok": True, "result": {"run_id": native["run_id"], "status": "cancelled", "cleanup": "restored"}})
            raise AssertionError("Unexpected native operation: " + operation)

        await engine.bridge.client.aclose()
        engine.bridge.client = httpx.AsyncClient(transport=httpx.MockTransport(dispatch))
        tools = {tool.__name__: tool for tool in engine._tools(session, str(uuid.uuid4()), emit)}
        task = asyncio.create_task(tools["run_scenario"]("contract:cancel", 0, 80, 0))
        try:
            await asyncio.wait_for(started.wait() if cancel_during_start else observed.wait(), 2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await asyncio.wait_for(task, 5)
            assert not native["active"] and native["starts"] == 1 and native["cleanups"] == 1
            cancellation = next(payload for kind, payload in events if kind == "scenario_cancel")
            assert cancellation["response"]["result"]["cleanup"] == "restored"
        finally:
            reply_released.set()
            if not task.done():
                task.cancel()
            await asyncio.gather(task, return_exceptions=True)
