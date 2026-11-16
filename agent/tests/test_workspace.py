import asyncio
import base64
import json
import os
from pathlib import Path
import sys
import uuid

import pytest

from rml_agent.workspace import MAX_FILE_BYTES, Workspace, digest, run_process, safe_relative


@pytest.fixture
def workspace(tmp_path):
    return Workspace(tmp_path / "workspaces", str(uuid.uuid4()))


@pytest.mark.parametrize("name", ["../secret", "/absolute", "C:/secret", "C:secret", "a\\b", "a//b", "a/./b",
                                  "a/../b", "name:stream", "con", "NUL.json", "a/LPT1.txt", "x.", "x ", "a\x00b"])
def test_cross_platform_escape_and_alias_paths_are_rejected(workspace, name):
    with pytest.raises(ValueError):
        workspace.path(name)
    with pytest.raises(ValueError):
        safe_relative(name)


def test_project_metadata_matches_compiler_format_and_ownership(workspace):
    metadata = json.loads(workspace.path("pack.mcmeta").read_text())
    assert metadata["pack"]["pack_format"] == 3
    assert metadata["rml"]["id"] == workspace.pack_id
    assert metadata["rml"]["language_version"] == 1
    original = workspace.read_file("pack.mcmeta")
    metadata["rml"]["id"] = "someone_else"
    with pytest.raises(ValueError, match="ownership"):
        workspace.change("pack.mcmeta", json.dumps(metadata), original["sha256"])
    with pytest.raises(ValueError, match="cannot be deleted"):
        workspace.change("pack.mcmeta", None, original["sha256"])
    assert workspace.read_file("pack.mcmeta") == original


def test_duplicate_manifest_keys_cannot_hide_an_owner_change(workspace):
    original = workspace.read_file("pack.mcmeta")
    content = '{"rml":{"id":"' + workspace.pack_id + '","id":"other"}}'
    with pytest.raises(ValueError, match="Duplicate"):
        workspace.change("pack.mcmeta", content, original["sha256"])
    assert workspace.read_file("pack.mcmeta") == original


def test_digest_conflict_diff_binary_roundtrip_and_delete(workspace):
    name = "data/example/function/start.mcfunction"
    created = workspace.change(name, "say hello\n", "")
    assert "+say hello" in created["diff"]
    with pytest.raises(ValueError, match="Digest conflict"):
        workspace.change(name, "say overwrite\n", "")
    with pytest.raises(ValueError, match="Digest conflict"):
        workspace.change(name, None, "b" * 64)
    updated = workspace.change(name, "say good bye\n", created["sha256"])
    assert "-say hello" in updated["diff"] and "+say good bye" in updated["diff"]
    workspace.change(name, None, updated["sha256"])
    assert not workspace.path(name).exists()
    blob = b"\x00\xff\xfe\x01"
    binary = workspace.change("assets/example/blob.bin", base64.b64encode(blob).decode(), "", "base64")
    assert binary["binary"] and binary["sha256"] == digest(blob)
    assert workspace.read_file("assets/example/blob.bin", "base64")["content"] == base64.b64encode(blob).decode()


def test_missing_read_does_not_create_directories_and_limits_precede_write(workspace):
    with pytest.raises(FileNotFoundError):
        workspace.read_file("missing/nested/file")
    assert not (workspace.source / "missing").exists()
    with pytest.raises(ValueError, match="size limit"):
        workspace.change("huge", "x" * (MAX_FILE_BYTES + 1), "")
    assert not (workspace.source / "huge").exists()


def test_sessions_cannot_read_each_others_sources(workspace, tmp_path):
    other = Workspace(tmp_path / "workspaces", str(uuid.uuid4()))
    other.change("secret", "private", "")
    with pytest.raises(ValueError):
        workspace.read_file("../../" + other.session_id + "/source/secret")
    assert all(item["path"] != "secret" for item in workspace.list_files())


def test_symlink_directories_and_root_are_rejected(workspace, tmp_path):
    outside = tmp_path / "outside"
    outside.mkdir()
    (outside / "secret").write_text("private")
    link = workspace.source / "linked"
    try:
        link.symlink_to(outside, target_is_directory=True)
    except OSError:
        pytest.skip("Creating symlinks requires privileges on this platform")
    with pytest.raises(ValueError, match="Symlinks"):
        workspace.read_file("linked/secret")
    with pytest.raises(ValueError, match="Symlinks"):
        workspace.list_files()
    with pytest.raises(ValueError, match="Symlinks"):
        Workspace(link, str(uuid.uuid4()))
    assert (outside / "secret").read_text() == "private"


def test_hardlinked_files_cannot_be_read_or_replaced(workspace, tmp_path):
    outside = tmp_path / "outside-secret"
    outside.write_text("private")
    try:
        os.link(outside, workspace.source / "linked")
    except OSError:
        pytest.skip("Hard links unavailable on this filesystem")
    with pytest.raises(ValueError, match="Hard-linked"):
        workspace.read_file("linked")
    with pytest.raises(ValueError, match="Hard-linked"):
        workspace.change("linked", "changed", digest(b"private"))
    assert outside.read_text() == "private"


@pytest.mark.asyncio
async def test_real_subprocess_output_and_nonzero_exit_are_preserved():
    chunks = []
    result = await run_process([sys.executable, "-u", "-c",
        "import sys; print('actual stdout'); print('actual stderr', file=sys.stderr); sys.exit(7)"],
        os.environ.copy(), lambda stream, text: chunks.append((stream, text)))
    assert result["returncode"] == 7
    assert "actual stdout" in result["stdout"] and "actual stderr" in result["stderr"]
    assert {stream for stream, _ in chunks} == {"stdout", "stderr"}


@pytest.mark.asyncio
async def test_cancellation_reaps_real_process_and_preserves_output(monkeypatch):
    original = asyncio.create_subprocess_exec
    children = []
    async def capture(*args, **kwargs):
        process = await original(*args, **kwargs)
        children.append(process)
        return process
    monkeypatch.setattr(asyncio, "create_subprocess_exec", capture)
    ready = asyncio.Event()
    chunks = []
    def output(stream, text):
        chunks.append(text)
        if "started" in "".join(chunks):
            ready.set()
    task = asyncio.create_task(run_process([sys.executable, "-u", "-c",
        "import time; print('started', flush=True); time.sleep(120)"], os.environ.copy(), output))
    await asyncio.wait_for(ready.wait(), 10)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(task, 10)
    assert len(children) == 1 and children[0].returncode is not None
    assert "started" in "".join(chunks)


@pytest.mark.asyncio
async def test_cancel_during_spawn_still_reaps_real_process(monkeypatch):
    original = asyncio.create_subprocess_exec
    spawned, release = asyncio.Event(), asyncio.Event()
    children = []
    async def delayed(*args, **kwargs):
        process = await original(*args, **kwargs)
        children.append(process)
        spawned.set()
        await release.wait()
        return process
    monkeypatch.setattr(asyncio, "create_subprocess_exec", delayed)
    task = asyncio.create_task(run_process([sys.executable, "-c", "import time; time.sleep(120)"],
                                          os.environ.copy(), lambda *_: None))
    await asyncio.wait_for(spawned.wait(), 10)
    task.cancel()
    release.set()
    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(task, 10)
    assert children[0].returncode is not None


@pytest.mark.asyncio
async def test_actual_rml_compiler_produces_distinct_owned_builds(workspace):
    # Opt in with the repository's real built compiler JAR; no compiler/model double.
    configured = os.environ.get("RML_TEST_COMPILER")
    if not configured:
        pytest.skip("Set RML_TEST_COMPILER to the existing RML compiler JAR for integration coverage")
    import zipfile
    compiler = Path(configured).absolute()
    workspace.change(f"data/{workspace.pack_id}/function/greet.mcfunction", "say hello\n", "")
    events = []
    first = await workspace.compile(compiler, lambda *event: events.append(event))
    second = await workspace.compile(compiler, lambda *event: events.append(event))
    assert first["returncode"] == second["returncode"] == 0
    assert first["id"] != second["id"]
    assert digest(workspace.build_path(first["id"]).read_bytes()) == first["sha256"]
    with zipfile.ZipFile(workspace.build_path(first["id"])) as archive:
        metadata = json.loads(archive.read("rml.info"))
        assert metadata[0]["modid"] == workspace.pack_id
    assert any(event[1].get("phase") == "output" for event in events)


@pytest.mark.asyncio
async def test_output_journal_failure_stops_a_noisy_real_child(monkeypatch):
    original = asyncio.create_subprocess_exec
    children = []
    async def capture(*args, **kwargs):
        process = await original(*args, **kwargs)
        children.append(process)
        return process
    monkeypatch.setattr(asyncio, "create_subprocess_exec", capture)
    def unavailable(*_):
        raise OSError("journal unavailable")
    with pytest.raises(OSError, match="journal unavailable"):
        await asyncio.wait_for(run_process([sys.executable, "-u", "-c",
            "while True: print('x' * 8192, flush=True)"], os.environ.copy(), unavailable), 10)
    assert children[0].returncode is not None
