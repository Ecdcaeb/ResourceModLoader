import uuid

import pytest

from rml_agent.store import Conflict, Store, canonical_uuid, principal_id


def uid():
    return str(uuid.uuid4())


def test_identity_and_tokens_survive_restart_and_are_isolated(tmp_path):
    instance, other_instance, player = uid(), uid(), uid()
    path = tmp_path / "journal.sqlite3"
    first = Store(path, instance)
    console = first.open_session("console")
    user = first.open_session(player)
    assert console["id"] != user["id"]
    assert console["token"] != user["token"]
    assert first.open_session("console") == console
    first.close()
    reopened = Store(path, instance)
    assert reopened.open_session("console") == console
    reopened.close()
    other = Store(path, other_instance)
    assert other.session(console["id"]) is None
    assert other.open_session("console")["token"] != console["token"]
    other.close()


def test_request_id_binds_payload_and_only_one_distinct_run_can_start(tmp_path):
    store = Store(tmp_path / "journal.sqlite3", uid())
    session = store.open_session("console")["id"]
    request = uid()
    run, created = store.submit(session, request, "make a function")
    assert created
    assert store.submit(session, request, "make a function") == (run, False)
    with pytest.raises(Conflict, match="different message"):
        store.submit(session, request, "delete everything")
    with pytest.raises(Conflict, match="already active"):
        store.submit(session, uid(), "another request")
    assert len(store.events(session, 0)) == 1
    store.status(session, run["id"], "completed")
    same, created = store.submit(session, request, "make a function")
    assert not created and same["status"] == "completed"
    assert store.submit(session, uid(), "new work")[1]
    store.close()


def test_restart_interrupts_without_replaying_and_journal_reconnects(tmp_path):
    instance = uid()
    path = tmp_path / "journal.sqlite3"
    store = Store(path, instance)
    one, two = store.open_session("console"), store.open_session(uid())
    run, _ = store.submit(one["id"], uid(), "compile this")
    store.status(one["id"], run["id"], "running")
    before = store.last_seq(one["id"])
    hidden = store.emit(two["id"], None, "assistant", {"text": "private"})
    committed = store.emit(one["id"], run["id"], "bridge_result", {"state": "committed"})
    store.close()
    reopened = Store(path, instance)
    reopened.interrupt()
    tail = reopened.events(one["id"], before)
    assert [event["type"] for event in tail] == ["bridge_result", "run_interrupted"]
    assert tail[0]["seq"] == committed and all(event["seq"] != hidden for event in tail)
    assert reopened.active(one["id"]) is None
    assert reopened.run(one["id"], run["id"])["status"] == "interrupted"
    end = reopened.last_seq(one["id"])
    reopened.interrupt()
    assert reopened.last_seq(one["id"]) == end
    same, created = reopened.submit(one["id"], run["request_id"], run["message"])
    assert not created and same["status"] == "interrupted"
    reopened.close()


def test_terminal_result_cannot_be_replaced_by_late_cancellation(tmp_path):
    store = Store(tmp_path / "journal.sqlite3", uid())
    session = store.open_session("console")["id"]
    run, _ = store.submit(session, uid(), "inspect")
    store.status(session, run["id"], "completed", {"committed": True})
    store.status(session, run["id"], "cancelled")
    assert store.run(session, run["id"])["status"] == "completed"
    assert [event["type"] for event in store.events(session, 0)] == ["user", "run_completed"]
    store.close()


def test_build_records_are_session_owned_and_immutable(tmp_path):
    import sqlite3
    store = Store(tmp_path / "journal.sqlite3", uid())
    one, two = store.open_session("console"), store.open_session(uid())
    build = {"id": uid(), "sha256": "a" * 64, "artifact": "record.zip", "version": 0, "size": 1}
    store.save_build(one["id"], build)
    with pytest.raises(ValueError, match="Unknown build"):
        store.build(two["id"], build["id"])
    with pytest.raises(sqlite3.IntegrityError):
        store.save_build(one["id"], dict(build, sha256="b" * 64))
    assert store.build(one["id"], build["id"])["sha256"] == "a" * 64
    store.close()


@pytest.mark.parametrize("value", ["../console", "Console", "", "0", "a" * 32])
def test_principal_requires_literal_console_or_canonical_uuid(value):
    with pytest.raises(ValueError):
        principal_id(value)
    with pytest.raises(ValueError):
        canonical_uuid(value)
