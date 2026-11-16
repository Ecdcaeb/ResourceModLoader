"""Terminal transcript assembly for ADK delta and final-message events."""
from __future__ import annotations

from collections import deque

from .client import event_text, safe_text, TERMINAL_EVENTS


class Transcript:
    def __init__(self, limit: int = 200_000):
        self.limit = limit
        self.blocks: deque[list[str]] = deque()
        self.pending: dict[tuple[str, str], list[str]] = {}

    def append(self, text: str) -> None:
        self.blocks.append([safe_text(text).rstrip()])

    def event(self, event: dict) -> None:
        payload = event.get("payload", {})
        if event.get("type") == "assistant" and isinstance(payload, dict) and "text" in payload:
            key = (event.get("run_id", ""), payload.get("author", "assistant"))
            block = self.pending.get(key)
            text = safe_text(payload["text"])
            if payload.get("partial"):
                if block is None:
                    block = ["assistant> "]
                    self.blocks.append(block)
                    self.pending[key] = block
                block[0] += text
            else:
                if block is None:
                    self.append("assistant> " + text)
                else:
                    block[0] = "assistant> " + text
                    del self.pending[key]
        else:
            self.append(event_text(event))
            if event.get("type") in TERMINAL_EVENTS:
                for key in list(self.pending):
                    if key[0] == event.get("run_id"):
                        del self.pending[key]

    def text(self) -> str:
        size = sum(len(block[0]) + 1 for block in self.blocks)
        while size > self.limit and len(self.blocks) > 1:
            removed = self.blocks.popleft()
            size -= len(removed[0]) + 1
            for key, block in list(self.pending.items()):
                if block is removed:
                    del self.pending[key]
        if self.blocks and len(self.blocks[0][0]) > self.limit:
            self.blocks[0][0] = self.blocks[0][0][-self.limit:]
        return "\n".join(block[0] for block in self.blocks) + "\n"
