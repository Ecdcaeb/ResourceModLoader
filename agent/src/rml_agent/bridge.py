"""Authenticated native calls with honest cancellation and no mutation retries."""
from __future__ import annotations

import asyncio
import ipaddress
import uuid
from urllib.parse import urlsplit

import httpx


def loopback_url(url: str) -> str:
    parsed = urlsplit(url)
    if parsed.scheme != "http" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("Bridge URL must be a loopback HTTP origin")
    try:
        local = ipaddress.ip_address(parsed.hostname).is_loopback
    except ValueError:
        local = parsed.hostname == "localhost"
    if not local:
        raise ValueError("Bridge must be on loopback")
    return url.rstrip("/")


class Bridge:
    def __init__(self, endpoint: str, token: str):
        if not token:
            raise ValueError("RML_BRIDGE_TOKEN is required")
        self.endpoint = loopback_url(endpoint)
        self.client = httpx.AsyncClient(headers={"Authorization": "Bearer " + token},
                                        timeout=httpx.Timeout(65, connect=5), trust_env=False)

    async def close(self) -> None:
        await self.client.aclose()

    async def invoke(self, principal: str, session_id: str, operation: str, arguments: dict, emit) -> dict:
        request_id = str(uuid.uuid4())
        identity = {"request_id": request_id, "principal": principal, "session_id": session_id}
        payload = dict(identity, operation=operation, arguments=arguments)
        emit("bridge_request", {"request_id": request_id, "operation": operation, "arguments": arguments})

        async def send():
            try:
                response = await self.client.post(self.endpoint + "/invoke", json=payload)
                response.raise_for_status()
                result = response.json()
                if not isinstance(result, dict) or not isinstance(result.get("ok"), bool):
                    raise ValueError("Malformed bridge response")
                emit("bridge_result", {"request_id": request_id, "operation": operation, "response": result})
                return result
            except (httpx.HTTPError, ValueError):
                # A disconnected HTTP response cannot establish whether the command committed.
                result = {"ok": False, "state": "unknown", "diagnostics": [{
                    "code": "bridge_outcome_unknown", "message": "Native outcome is unknown. Inspect runtime before retrying; this operation was not retried."}],
                    "request_id": request_id}
                emit("bridge_result", {"request_id": request_id, "operation": operation, "response": result})
                return result

        pending = asyncio.create_task(send())
        try:
            return await asyncio.shield(pending)
        except asyncio.CancelledError:
            try:
                response = await self.client.post(self.endpoint + "/cancel", json=identity, timeout=3)
                response.raise_for_status()
                cancellation = response.json()
                emit("bridge_cancel", {"request_id": request_id, "response": cancellation})
            except (httpx.HTTPError, ValueError):
                emit("bridge_cancel", {"request_id": request_id, "state": "unknown",
                                       "message": "Could not confirm cancellation; native work may have committed."})
            # Preserve a response already being delivered, including committed operations.
            try:
                await asyncio.wait_for(asyncio.shield(pending), 3)
            except asyncio.TimeoutError:
                pending.cancel()
                await asyncio.gather(pending, return_exceptions=True)
                emit("bridge_result", {"request_id": request_id, "operation": operation,
                                       "response": {"ok": False, "state": "unknown", "diagnostics": [{
                                           "code": "bridge_outcome_unknown", "message": "Cancelled waiting for native result; execution may already have committed. Inspect runtime before retrying."}]}})
            raise
