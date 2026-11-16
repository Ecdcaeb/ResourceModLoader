"""Transport boundary tests; these fixtures do not represent real Minecraft execution."""
import asyncio
import json
import uuid

import httpx
import pytest

from rml_agent.bridge import Bridge, loopback_url


@pytest.mark.parametrize("url", ["https://127.0.0.1:1", "http://example.com", "http://127.0.0.1/path",
                                 "http://user@127.0.0.1", "http://127.0.0.1?secret=1", "file:///tmp"])
def test_bridge_requires_an_authenticated_loopback_origin(url):
    with pytest.raises(ValueError):
        loopback_url(url)


@pytest.mark.asyncio
async def test_unknown_native_outcome_is_explicit_and_never_retried():
    bridge = Bridge("http://127.0.0.1:9", "transport-test-secret")
    await bridge.client.aclose()
    requests, events = [], []
    async def timeout(request):
        requests.append(request)
        raise httpx.ReadTimeout("response lost after dispatch", request=request)
    bridge.client = httpx.AsyncClient(transport=httpx.MockTransport(timeout))
    try:
        result = await bridge.invoke("console", str(uuid.uuid4()), "command", {"command": "say hello"},
                                     lambda *event: events.append(event))
        assert len(requests) == 1
        assert result["state"] == "unknown" and not result["ok"]
        assert result["diagnostics"][0]["code"] == "bridge_outcome_unknown"
        assert "transport-test-secret" not in json.dumps(events)
    finally:
        await bridge.close()


@pytest.mark.asyncio
async def test_cancellation_targets_exact_request_and_preserves_returned_response():
    bridge = Bridge("http://127.0.0.1:9", "transport-test-secret")
    await bridge.client.aclose()
    invoked, cancelled = asyncio.Event(), asyncio.Event()
    requests, events = [], []
    response_body = {"ok": False, "state": "cancelled", "diagnostics": [{"code": "cancelled", "message": "Queued operation cancelled"}]}
    async def dispatch(request):
        payload = json.loads(request.content)
        requests.append((request.url.path, payload))
        if request.url.path == "/invoke":
            invoked.set()
            await cancelled.wait()
            return httpx.Response(200, json=response_body)
        assert request.url.path == "/cancel"
        cancelled.set()
        return httpx.Response(200, json={"ok": True, "state": "cancelled"})
    bridge.client = httpx.AsyncClient(transport=httpx.MockTransport(dispatch))
    try:
        session_id = str(uuid.uuid4())
        task = asyncio.create_task(bridge.invoke("console", session_id, "apply", {"candidate": "fixture", "revision": 1},
                                                 lambda *event: events.append(event)))
        await asyncio.wait_for(invoked.wait(), 1)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await asyncio.wait_for(task, 2)
        assert [path for path, _ in requests] == ["/invoke", "/cancel"]
        invoke, cancel = requests[0][1], requests[1][1]
        assert cancel == {key: invoke[key] for key in ("request_id", "principal", "session_id")}
        results = [payload for kind, payload in events if kind == "bridge_result"]
        assert len(results) == 1 and results[0]["response"] == response_body
    finally:
        await bridge.close()
