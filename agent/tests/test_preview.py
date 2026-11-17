"""Preview process and ownership tests; fixtures are not Minecraft instances."""
import asyncio
import json
import os
import subprocess
import sys
import uuid
import zipfile

import pytest

from rml_agent.preview import PreviewManager
from rml_agent.workspace import digest


def _profile(game_dir, mode="client"):
    return {"version": 1, "mode": mode, "game_directory": str(game_dir), "java": sys.executable,
            "classpath": ["wrapper.jar", "game.jar"], "jvm_args": ["-Dnative.path=fake"],
            "main_class": "fixture.Main", "arguments": ["--gameDir", "{preview}"], "shared_mods": []}


def _record(root, session_id=None):
    preview_id = str(uuid.uuid4())
    directory = root / preview_id
    directory.mkdir(parents=True)
    metadata = {"preview_id": preview_id, "session_id": session_id or str(uuid.uuid4()),
                "build_id": str(uuid.uuid4()), "sha256": "a" * 64, "mode": "client",
                "status": "starting", "directory": str(directory), "log_tail": "", "error": None,
                "join_address": None}
    return {"meta": metadata, "process": None, "bridge": None, "tasks": [], "ready": directory / "ready.json"}, directory


@pytest.mark.asyncio
async def test_prepare_replaces_old_pack_and_installs_exact_build_bytes(tmp_path):
    game = tmp_path / "game"
    (game / "mods").mkdir(parents=True)
    (game / "config").mkdir()
    (game / "resourcepacks").mkdir()
    (game / "scripts").mkdir()
    (game / "mods" / "old-pack.jar").write_bytes(b"old")
    with zipfile.ZipFile(game / "mods" / "old-pack.jar", "w") as package:
        package.writestr("rml.info", json.dumps({"modid": "test.pack"}))
        package.writestr("old", b"old")
    (game / "mods" / "shared.jar").write_bytes(b"shared")
    (game / "config" / "rml-agent.json").write_text("secret")
    (game / "config" / "other.cfg").write_text("keep")
    (game / "options.txt").write_text("fullscreen:true\npauseOnLostFocus:true\nlang:en_us\n")
    build = tmp_path / "candidate.zip"
    candidate = b"immutable candidate bytes"
    build.write_bytes(candidate)
    manager = PreviewManager(None, tmp_path / "state")
    manager.profile = _profile(game)
    manager.profile["shared_mods"] = [str(game / "mods" / "shared.jar")]
    record, directory = _record(tmp_path / "state")
    record["meta"]["sha256"] = digest(candidate)
    manager._prepare(record, build, "test.pack", 42)
    assert (directory / "mods" / "rml-preview-content.zip").read_bytes() == candidate
    assert not (directory / "mods" / "old-pack.jar").exists()
    assert not (directory / "mods" / "shared.jar").exists()
    assert not (directory / "config" / "rml-agent.json").exists()
    assert (directory / "config" / "other.cfg").read_text() == "keep"
    options = dict(line.split(":", 1) for line in (directory / "options.txt").read_text().splitlines())
    assert options["fullscreen"] == "false" and options["pauseOnLostFocus"] == "false" and options["lang"] == "en_us"
    assert record["meta"]["sha256"] == digest(candidate)


@pytest.mark.asyncio
async def test_session_ownership_and_recovery_never_adopts_saved_pid(tmp_path):
    root = tmp_path / "previews"
    manager = PreviewManager(None, root)
    root.mkdir()
    record, directory = _record(root)
    manager.previews[record["meta"]["preview_id"]] = record
    manager._save(record)
    wrong_session = str(uuid.uuid4())
    with pytest.raises(KeyError):
        await manager.status(wrong_session, record["meta"]["preview_id"])
    recovered = PreviewManager(None, root)
    await recovered.initialize()
    status = await recovered.status(record["meta"]["session_id"], record["meta"]["preview_id"])
    assert status["status"] == "interrupted"
    assert recovered.previews[record["meta"]["preview_id"]]["process"] is None
    assert directory.exists()


@pytest.mark.asyncio
async def test_command_uses_wrapper_without_shell_and_environment_scrubs_secrets(tmp_path, monkeypatch):
    game = tmp_path / "game"
    game.mkdir()
    manager = PreviewManager(None, tmp_path / "state", "MODEL_SECRET")
    manager.profile = _profile(game)
    record, _ = _record(tmp_path / "state")
    monkeypatch.setenv("RML_BRIDGE_TOKEN", "bridge-private")
    monkeypatch.setenv("RML_SERVICE_TOKEN", "service-private")
    monkeypatch.setenv("RML_PREVIEW_PROFILE", "profile-private")
    monkeypatch.setenv("MODEL_SECRET", "model-private")
    env = manager._environment(record, "fresh-preview-token", 17)
    assert not {"RML_BRIDGE_TOKEN", "RML_SERVICE_TOKEN", "RML_PREVIEW_PROFILE", "MODEL_SECRET"} & env.keys()
    assert env["RML_PREVIEW_TOKEN"] == "fresh-preview-token"
    assert env["RML_PREVIEW_SEED"] == "17"
    assert "fresh-preview-token" not in json.dumps(manager._public(record))


@pytest.mark.asyncio
async def test_cleanup_rejects_linked_preview_contents_and_retains_ownership(tmp_path):
    manager = PreviewManager(None, tmp_path / "state")
    manager.root.mkdir()
    record, directory = _record(manager.root)
    target = tmp_path / "outside"
    target.mkdir()
    (target / "keep.txt").write_text("outside preview")
    link = directory / "linked"
    junction = False
    try:
        link.symlink_to(target, target_is_directory=True)
    except (OSError, NotImplementedError):
        if sys.platform != "win32":
            pytest.skip("Directory links unavailable on this platform")
        result = subprocess.run('cmd.exe /d /v:off /c mklink /J "%RML_TEST_LINK%" "%RML_TEST_TARGET%"',
                                env=dict(os.environ, RML_TEST_LINK=str(link), RML_TEST_TARGET=str(target)), capture_output=True)
        if result.returncode:
            pytest.skip("Windows directory junctions unavailable: " + (result.stdout + result.stderr).decode(errors="replace"))
        junction = True
    try:
        removed = await manager._dispose(record)
        assert not removed
        assert directory.exists() and (target / "keep.txt").read_text() == "outside preview"
    finally:
        if junction:
            os.rmdir(link)
        else:
            link.unlink()


@pytest.mark.asyncio
async def test_child_stdin_eof_guard_contract_with_isolated_process(tmp_path, monkeypatch):
    """An isolated Python fixture verifies pipe ownership, not Minecraft behavior."""
    manager = PreviewManager(None, tmp_path / "state")
    manager.root.mkdir()
    record, directory = _record(manager.root)
    process = await asyncio.create_subprocess_exec(sys.executable, "-c", "import sys; sys.stdin.read()",
                                                   stdin=asyncio.subprocess.PIPE,
                                                   stdout=asyncio.subprocess.PIPE)
    record["process"] = process
    assert process.returncode is None
    await manager._stop_process(record)
    assert process.returncode == 0
@pytest.mark.asyncio
async def test_cancellation_during_spawn_reaps_exact_child_via_stdin_eof(tmp_path, monkeypatch):
    game = tmp_path / "game"
    game.mkdir()
    root = tmp_path / "state"
    root.mkdir()
    manager = PreviewManager(None, root)
    manager.profile = _profile(game)
    entered = asyncio.Event()
    release = asyncio.Event()
    spawned = []

    real_spawn = asyncio.create_subprocess_exec
    async def delayed_spawn(*args, **kwargs):
        entered.set()
        await release.wait()
        process = await real_spawn(
            sys.executable, "-c", "import sys; sys.stdin.read()",
            stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE)
        spawned.append(process)
        return process

    monkeypatch.setattr(asyncio, "create_subprocess_exec", delayed_spawn)
    build = tmp_path / "build.zip"
    build.write_bytes(b"candidate")
    task = asyncio.create_task(manager.create(str(uuid.uuid4()), str(uuid.uuid4()), build,
                                              digest(b"candidate"), "test.pack", 11, lambda *_: None))
    await asyncio.wait_for(entered.wait(), 2)
    task.cancel()
    release.set()
    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(task, 2)
    assert len(spawned) == 1
    assert spawned[0].returncode == 0


@pytest.mark.asyncio
async def test_cancelled_close_still_reaps_child_and_removes_owned_save(tmp_path, monkeypatch):
    manager = PreviewManager(None, tmp_path / "state")
    manager.root.mkdir()
    record, directory = _record(manager.root)
    process = await asyncio.create_subprocess_exec(sys.executable, "-c", "import sys; sys.stdin.read()",
                                                   stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE)
    record["process"] = process
    record["meta"]["status"] = "running"
    manager.previews[record["meta"]["preview_id"]] = record
    stopping, release = asyncio.Event(), asyncio.Event()
    original_stop = manager._stop_process

    async def delayed_stop(value):
        stopping.set()
        await release.wait()
        await original_stop(value)

    monkeypatch.setattr(manager, "_stop_process", delayed_stop)
    task = asyncio.create_task(manager.close_preview(record["meta"]["session_id"], record["meta"]["preview_id"]))
    try:
        await asyncio.wait_for(stopping.wait(), 2)
        task.cancel()
        release.set()
        with pytest.raises(asyncio.CancelledError):
            await asyncio.wait_for(task, 5)
        assert process.returncode == 0 and not directory.exists()
        assert (await manager.status(record["meta"]["session_id"], record["meta"]["preview_id"]))["status"] == "closed"
    finally:
        release.set()
        await asyncio.gather(task, return_exceptions=True)
        if process.returncode is None:
            process.kill()
            await process.wait()
