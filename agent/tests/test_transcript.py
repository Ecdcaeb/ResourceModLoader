from rml_agent.transcript import Transcript


def assistant(text, partial, run="run"):
    return {"type": "assistant", "run_id": run, "payload": {"text": text, "partial": partial, "author": "rml_operator"}}


def test_final_reply_replaces_deltas_without_losing_tool_output():
    transcript = Transcript()
    transcript.event(assistant("I will ", True))
    transcript.event({"type": "tool_result", "run_id": "run", "payload": {"name": "apply_candidate", "result": {"ok": True}}})
    transcript.event(assistant("apply it.", True))
    transcript.event(assistant("I will apply it.", False))
    rendered = transcript.text()
    assert rendered.count("I will apply it.") == 1
    assert "apply_candidate" in rendered and '"ok": true' in rendered
    assert rendered.index("I will apply it.") < rendered.index("apply_candidate")


def test_cancelled_partial_reply_cannot_be_overwritten_by_another_run():
    transcript = Transcript()
    transcript.event(assistant("First request", True, "first"))
    transcript.event({"type": "run_cancelled", "run_id": "first", "payload": {"message": "Cancelled"}})
    transcript.event(assistant("Second", True, "second"))
    transcript.event(assistant("Second request finished", False, "second"))
    rendered = transcript.text()
    assert "First request" in rendered and "Cancelled" in rendered
    assert rendered.count("Second request finished") == 1
    assert not transcript.pending
