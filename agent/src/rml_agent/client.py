from __future__ import annotations

import asyncio
from dataclasses import dataclass
import json
import os
from pathlib import Path
from typing import Any, AsyncIterator
from urllib.parse import urlsplit

import httpx


class ClientError(RuntimeError):
    pass


@dataclass(frozen=True)
class Connection:
    endpoint: str
    session: str
    token: str
    file: Path | None = None
    endpoint_override: str | None = None

    @classmethod
    def load(cls, *, file: str | None, endpoint: str | None, session: str | None,
             token: str | None, token_env: str = "RML_AGENT_TOKEN") -> Connection:
        source = Path(file).expanduser().resolve() if file else None
        value = json.loads(source.read_text(encoding="utf-8")) if source else {}
        address = (endpoint or value.get("connect", "")).rstrip("/")
        identity = session or value.get("session", "")
        secret = token or os.environ.get(token_env) or value.get("token", "")
        parsed = urlsplit(address)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
            raise ClientError("Provide an http(s) service endpoint using --connect or --connection.")
        if not identity or not secret:
            raise ClientError("A session and its access token are required; use the connection file from /rml agent start.")
        return cls(address, identity, secret, source, endpoint)

    def refreshed(self) -> Connection:
        if self.file is None:
            return self
        # A local supervisor may replace the connection file when its service restarts.
        return self.load(file=str(self.file), endpoint=self.endpoint_override, session=None, token=None, token_env="")


class AgentClient:
    def __init__(self, connection: Connection):
        self.connection = connection
        self.http = self._http(connection)

    @staticmethod
    def _http(connection: Connection) -> httpx.AsyncClient:
        return httpx.AsyncClient(base_url=connection.endpoint,
                                 headers={"Authorization": "Bearer " + connection.token},
                                 timeout=httpx.Timeout(30.0, connect=5.0))

    async def __aenter__(self) -> AgentClient:
        return self

    async def __aexit__(self, *_: Any) -> None:
        await self.http.aclose()

    async def refresh(self) -> None:
        connection = self.connection.refreshed()
        if connection != self.connection:
            previous = self.http
            self.connection = connection
            self.http = self._http(connection)
            await previous.aclose()

    def path(self, suffix: str = "") -> str:
        return "/v1/sessions/" + self.connection.session + suffix

    @staticmethod
    def checked(response: httpx.Response) -> Any:
        try:
            value = response.json()
        except ValueError:
            raise ClientError(f"Service returned HTTP {response.status_code} without a JSON response.") from None
        if response.is_error:
            detail = value.get("detail", value) if isinstance(value, dict) else value
            raise ClientError(f"HTTP {response.status_code}: {json.dumps(detail, ensure_ascii=False)}")
        return value

    async def status(self) -> dict[str, Any]:
        return self.checked(await self.http.get(self.path()))

    async def submit(self, message: str, request_id: str) -> dict[str, Any]:
        # Submission is deliberately not retried: the durable journal resolves ambiguous responses.
        return self.checked(await self.http.post(self.path("/runs"), json={"request_id": request_id, "message": message}))

    async def cancel(self, run_id: str) -> dict[str, Any]:
        return self.checked(await self.http.post(self.path(f"/runs/{run_id}/cancel"), json={}))

    async def events(self, after: int = 0) -> AsyncIterator[dict[str, Any]]:
        async with self.http.stream("GET", self.path("/events"), params={"after": after},
                                    timeout=httpx.Timeout(45.0, connect=5.0)) as response:
            if response.is_error:
                await response.aread()
                self.checked(response)
            data: list[str] = []
            async for line in response.aiter_lines():
                if line.startswith("data:"):
                    data.append(line[5:].lstrip())
                elif not line and data:
                    value = json.loads("\n".join(data))
                    data.clear()
                    sequence = int(value["seq"])
                    if sequence > after:
                        after = sequence
                        yield value


def safe_text(value: Any) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2)
    return "".join(character if character in "\n\t" or ord(character) >= 32 and ord(character) != 127
                   else f"\\x{ord(character):02x}" for character in text)


def event_text(event: dict[str, Any]) -> str:
    kind = event.get("type", "event")
    payload = event.get("payload", {})
    if not isinstance(payload, dict):
        return f"{kind}> {safe_text(payload)}"
    if kind == "file_diff":
        return f"file> {safe_text(payload.get('path', ''))}\n{safe_text(payload.get('diff', payload))}"
    if kind == "tool_start":
        return f"tool> {safe_text(payload.get('name', ''))} {safe_text(payload.get('arguments', {}))}"
    if kind == "tool_result":
        return f"result> {safe_text(payload.get('name', ''))}\n{safe_text(payload.get('result', payload))}"
    text = payload.get("text", payload.get("message", payload))
    return f"{kind}> {safe_text(text)}"


def active_run(status: dict[str, Any]) -> str | None:
    value = status.get("active_run")
    if isinstance(value, dict):
        return value.get("run_id") or value.get("id")
    return value if isinstance(value, str) and value else None


TERMINAL_EVENTS = {"run_completed", "run_failed", "run_cancelled", "run_interrupted"}


async def run_message(connection: Connection, message: str, request_id: str, json_events: bool) -> int:
    async with AgentClient(connection) as client:
        response = await client.submit(message, request_id)
        run_id = response["run_id"]
        cursor = 0
        try:
            while True:
                try:
                    async for event in client.events(cursor):
                        cursor = int(event["seq"])
                        if event.get("run_id") != run_id:
                            continue
                        if json_events or event["type"] != "assistant" or not event.get("payload", {}).get("partial"):
                            print(json.dumps(event, ensure_ascii=False) if json_events else event_text(event), flush=True)
                        if event["type"] in TERMINAL_EVENTS:
                            return 0 if event["type"] == "run_completed" else 1
                except (httpx.TransportError, OSError):
                    await asyncio.sleep(1)
                    await client.refresh()
        except asyncio.CancelledError:
            await asyncio.shield(client.cancel(run_id))
            raise
