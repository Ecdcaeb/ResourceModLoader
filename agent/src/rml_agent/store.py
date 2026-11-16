"""Small SQLite journal, separate from ADK's conversation database."""
from __future__ import annotations

import json
import secrets
import sqlite3
import time
import uuid
from pathlib import Path


class Conflict(ValueError):
    pass


def canonical_uuid(value: str) -> str:
    parsed = str(uuid.UUID(value))
    if value != parsed:
        raise ValueError("Expected a canonical UUID")
    return parsed


def principal_id(value: str) -> str:
    return value if value == "console" else canonical_uuid(value)


class Store:
    def __init__(self, path: Path, instance: str):
        self.instance = canonical_uuid(instance)
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY, instance TEXT NOT NULL, principal TEXT NOT NULL,
                token TEXT NOT NULL, created REAL NOT NULL, UNIQUE(instance, principal));
            CREATE TABLE IF NOT EXISTS runs (
                id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
                request_id TEXT NOT NULL, message TEXT NOT NULL, status TEXT NOT NULL,
                created REAL NOT NULL, UNIQUE(session_id, request_id));
            CREATE UNIQUE INDEX IF NOT EXISTS one_active_run ON runs(session_id)
                WHERE status IN ('queued', 'running', 'cancelling');
            CREATE TABLE IF NOT EXISTS events (
                seq INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL REFERENCES sessions(id),
                run_id TEXT, type TEXT NOT NULL, payload TEXT NOT NULL);
            CREATE INDEX IF NOT EXISTS session_events ON events(session_id, seq);
            CREATE TABLE IF NOT EXISTS builds (
                id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
                sha256 TEXT NOT NULL, artifact TEXT NOT NULL, version INTEGER NOT NULL,
                size INTEGER NOT NULL);
        """)
        self.db.commit()

    def close(self) -> None:
        self.db.close()

    def session(self, session_id: str) -> dict | None:
        row = self.db.execute("SELECT * FROM sessions WHERE id=? AND instance=?",
                              (session_id, self.instance)).fetchone()
        return dict(row) if row else None

    def open_session(self, principal: str) -> dict:
        principal = principal_id(principal)
        with self.db:
            self.db.execute("INSERT OR IGNORE INTO sessions VALUES (?, ?, ?, ?, ?)",
                            (str(uuid.uuid4()), self.instance, principal,
                             secrets.token_urlsafe(32), time.time()))
            row = self.db.execute("SELECT * FROM sessions WHERE instance=? AND principal=?",
                                  (self.instance, principal)).fetchone()
        return dict(row)

    def emit(self, session_id: str, run_id: str | None, kind: str, payload: dict) -> int:
        with self.db:
            return self._emit(session_id, run_id, kind, payload)

    def _emit(self, session_id: str, run_id: str | None, kind: str, payload: dict) -> int:
        cursor = self.db.execute("INSERT INTO events(session_id,run_id,type,payload) VALUES(?,?,?,?)",
                                 (session_id, run_id, kind, json.dumps(payload, ensure_ascii=False)))
        return cursor.lastrowid

    def events(self, session_id: str, after: int, limit: int = 200) -> list[dict]:
        rows = self.db.execute("SELECT seq,type,run_id,payload FROM events WHERE session_id=? AND seq>? ORDER BY seq LIMIT ?",
                               (session_id, after, limit)).fetchall()
        return [dict(row, payload=json.loads(row["payload"])) for row in rows]

    def last_seq(self, session_id: str) -> int:
        return self.db.execute("SELECT COALESCE(MAX(seq), 0) FROM events WHERE session_id=?", (session_id,)).fetchone()[0]

    def active(self, session_id: str) -> dict | None:
        row = self.db.execute("SELECT * FROM runs WHERE session_id=? AND status IN ('queued','running','cancelling')",
                              (session_id,)).fetchone()
        return dict(row) if row else None

    def run(self, session_id: str, run_id: str) -> dict | None:
        row = self.db.execute("SELECT * FROM runs WHERE session_id=? AND id=?", (session_id, run_id)).fetchone()
        return dict(row) if row else None

    def submit(self, session_id: str, request_id: str, message: str) -> tuple[dict, bool]:
        canonical_uuid(request_id)
        with self.db:
            # The transaction serializes the lookup and insertion even across connections.
            self.db.execute("BEGIN IMMEDIATE")
            existing = self.db.execute("SELECT * FROM runs WHERE session_id=? AND request_id=?",
                                       (session_id, request_id)).fetchone()
            if existing:
                if existing["message"] != message:
                    raise Conflict("request_id was already used with a different message")
                return dict(existing), False
            if self.active(session_id):
                raise Conflict("A run is already active in this session")
            run_id = str(uuid.uuid4())
            self.db.execute("INSERT INTO runs VALUES(?,?,?,?,?,?)",
                            (run_id, session_id, request_id, message, "queued", time.time()))
            self._emit(session_id, run_id, "user", {"text": message})
        return self.run(session_id, run_id), True

    def status(self, session_id: str, run_id: str, status: str, payload: dict | None = None) -> None:
        with self.db:
            changed = self.db.execute("UPDATE runs SET status=? WHERE id=? AND session_id=? AND status IN ('queued','running','cancelling')",
                                      (status, run_id, session_id)).rowcount
            if changed and status != "cancelling":
                kind = "run_started" if status == "running" else "run_" + status
                self._emit(session_id, run_id, kind, payload or {})

    def interrupt(self) -> None:
        rows = self.db.execute("SELECT runs.* FROM runs JOIN sessions ON sessions.id=runs.session_id WHERE sessions.instance=? AND status IN ('queued','running','cancelling')",
                               (self.instance,)).fetchall()
        for row in rows:
            self.status(row["session_id"], row["id"], "interrupted",
                        {"message": "Service stopped before completion; external operations may have committed. Inspect runtime before retrying."})

    def save_build(self, session_id: str, build: dict) -> None:
        with self.db:
            self.db.execute("INSERT INTO builds VALUES(?,?,?,?,?,?)",
                            (build["id"], session_id, build["sha256"], build["artifact"], build["version"], build["size"]))

    def builds(self, session_id: str) -> list[dict]:
        return [dict(row) for row in self.db.execute("SELECT * FROM builds WHERE session_id=? ORDER BY rowid", (session_id,))]

    def build(self, session_id: str, build_id: str) -> dict:
        canonical_uuid(build_id)
        row = self.db.execute("SELECT * FROM builds WHERE id=? AND session_id=?", (build_id, session_id)).fetchone()
        if row is None:
            raise ValueError("Unknown build in this session")
        return dict(row)
