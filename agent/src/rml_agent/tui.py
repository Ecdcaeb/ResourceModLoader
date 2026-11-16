from __future__ import annotations

import asyncio
from collections import deque
import json
from typing import Any
import uuid

import httpx
from prompt_toolkit.application import Application
from prompt_toolkit.document import Document
from prompt_toolkit.filters import has_focus
from prompt_toolkit.key_binding import KeyBindings
from prompt_toolkit.layout import HSplit, Layout, Window
from prompt_toolkit.layout.controls import FormattedTextControl
from prompt_toolkit.styles import Style
from prompt_toolkit.widgets import Frame, TextArea

from .client import AgentClient, ClientError, Connection, TERMINAL_EVENTS, active_run
from .transcript import Transcript


class AgentTerminal:
    def __init__(self, connection: Connection):
        self.connection = connection
        self.client = AgentClient(connection)
        self.cursor = 0
        self.finished_runs: deque[str] = deque(maxlen=128)
        self.current_run: str | None = None
        self.connection_status = "connecting"
        self.model_status = ""
        self.submitting = False
        self.transcript = Transcript()
        self.transcript.append("RML Agent\n/status shows the session; /cancel cancels a request; /exit detaches.")
        self.output = TextArea(text=self.transcript.text(),
                               read_only=True, scrollbar=True, wrap_lines=True, focusable=True)
        self.prompt = TextArea(height=3, prompt="> ", multiline=False, wrap_lines=True)
        bindings = KeyBindings()

        @bindings.add("enter", filter=has_focus(self.prompt))
        def submit(event: Any) -> None:
            message = self.prompt.text.strip()
            if message:
                self.prompt.buffer.reset()
                event.app.create_background_task(self.submit(message))

        @bindings.add("c-c")
        def cancel(event: Any) -> None:
            event.app.create_background_task(self.cancel())

        @bindings.add("c-d")
        def detach(event: Any) -> None:
            event.app.exit()

        @bindings.add("tab")
        def focus(event: Any) -> None:
            event.app.layout.focus_next()

        self.application: Application[None] = Application(
            layout=Layout(HSplit([
                Window(FormattedTextControl(self.header), height=1, style="class:header"),
                self.output,
                Frame(self.prompt, title="Enter: send   Ctrl-C: cancel   Ctrl-D: detach   Tab: scroll history"),
            ]), focused_element=self.prompt),
            key_bindings=bindings, full_screen=True, mouse_support=True,
            style=Style.from_dict({"header": "reverse", "frame.label": "bold"}),
        )

    def header(self) -> str:
        running = " | running " + self.current_run[:8] if self.current_run else ""
        return f" RML Agent | {self.connection.session[:8]} | {self.connection_status}{running}{self.model_status} "

    def append(self, text: str) -> None:
        self.transcript.append(text)
        self.redraw()

    def redraw(self) -> None:
        text = self.transcript.text()
        self.output.buffer.set_document(Document(text, len(text)), bypass_readonly=True)
        self.application.invalidate()

    async def submit(self, message: str) -> None:
        if message in {"/exit", "/quit"}:
            self.application.exit()
            return
        if message == "/cancel":
            await self.cancel()
            return
        if message == "/status":
            try:
                self.append(json.dumps(await self.client.status(), ensure_ascii=False, indent=2))
            except (ClientError, httpx.HTTPError) as error:
                self.append(str(error))
            return
        if self.submitting or self.current_run:
            self.append("A request is running. Use /cancel before starting another.")
            return
        self.submitting = True
        request_id = str(uuid.uuid4())
        try:
            response = await self.client.submit(message, request_id)
            self.current_run = response["run_id"] if response["run_id"] not in self.finished_runs else None
        except (ClientError, httpx.HTTPError) as error:
            self.append(f"Request {request_id}: {error}")
            # A lost response does not establish whether the server accepted the message.
            try:
                self.current_run = active_run(await self.client.status())
            except (ClientError, httpx.HTTPError):
                self.append("Submission outcome is unknown; reconnect to recover its saved events. The message was not resent.")
        finally:
            self.submitting = False
            self.application.invalidate()

    async def cancel(self) -> None:
        try:
            run = self.current_run or active_run(await self.client.status())
            if run is None:
                self.append("No request is running. Ctrl-D detaches from this session.")
                return
            result = await self.client.cancel(run)
            self.append("cancel> " + json.dumps(result, ensure_ascii=False))
        except (ClientError, httpx.HTTPError) as error:
            self.append(str(error))

    async def receive(self) -> None:
        delay = 1
        while True:
            try:
                await self.client.refresh()
                self.connection = self.client.connection
                status = await self.client.status()
                self.current_run = active_run(status)
                self.model_status = " | model not configured" if not status.get("model_configured", True) else ""
                self.connection_status = "connected"
                self.application.invalidate()
                delay = 1
                async for event in self.client.events(self.cursor):
                    self.cursor = int(event["seq"])
                    if event["type"] == "run_started":
                        self.current_run = event.get("run_id")
                    elif event["type"] in TERMINAL_EVENTS:
                        self.finished_runs.append(event.get("run_id", ""))
                        if event.get("run_id") == self.current_run:
                            self.current_run = None
                    self.transcript.event(event)
                    self.redraw()
            except (ClientError, httpx.HTTPError, OSError, ValueError) as error:
                self.connection_status = "disconnected"
                self.append(f"Connection interrupted: {error}. Reconnecting from event {self.cursor}.")
            await asyncio.sleep(delay)
            delay = min(delay * 2, 8)

    async def run(self) -> None:
        async with self.client:
            receive = asyncio.create_task(self.receive())
            try:
                await self.application.run_async()
            finally:
                receive.cancel()
                try:
                    await receive
                except asyncio.CancelledError:
                    pass


def run_terminal(connection: Connection) -> None:
    asyncio.run(AgentTerminal(connection).run())
