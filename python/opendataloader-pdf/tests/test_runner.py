"""Unit tests for runner.py error-handling behaviour.

Regression: when the JAR fails, the streaming branch already wrote the
JAR's stdout to the console live, so the except handler must not re-emit
the captured copy. The quiet branch, conversely, has not surfaced anything
yet and is allowed to print the captured streams — but only once
(``CalledProcessError.output`` and ``.stdout`` are the same attribute).
"""

import io
import subprocess
import time
from unittest.mock import MagicMock

import pytest

from opendataloader_pdf import runner


class _FakeAsFile:
    def __init__(self, path):
        self._path = path

    def __enter__(self):
        return self._path

    def __exit__(self, *_args):
        return False


@pytest.fixture
def patched_jar(monkeypatch, tmp_path):
    """Bypass the real resources lookup so run_jar reaches subprocess."""
    fake_jar = tmp_path / "opendataloader-pdf-cli.jar"
    fake_jar.write_bytes(b"")
    fake_traversable = MagicMock()
    fake_traversable.joinpath = lambda *_a, **_kw: fake_jar
    monkeypatch.setattr(runner.resources, "files", lambda _pkg: fake_traversable)
    monkeypatch.setattr(runner.resources, "as_file", lambda p: _FakeAsFile(p))


def test_streaming_failure_does_not_duplicate_output(monkeypatch, capsys, patched_jar):
    """Streaming mode prints JAR output live; the except handler must not
    re-emit the captured copy on stderr."""
    jar_output = "Invalid page range format: '-10'\nusage: [options] ...\n"

    fake_process = MagicMock()
    fake_process.stdout = iter([jar_output])
    fake_process.wait.return_value = 2
    fake_process.__enter__ = lambda self: self
    fake_process.__exit__ = lambda self, *_a: False

    monkeypatch.setattr(runner.subprocess, "Popen", lambda *_a, **_kw: fake_process)

    with pytest.raises(subprocess.CalledProcessError):
        runner.run_jar(["--bogus"], quiet=False)

    captured = capsys.readouterr()
    # JAR text appears exactly once: the live streaming write.
    assert "Invalid page range format" in captured.out
    assert captured.out.count("usage: [options]") == 1
    # The except handler did NOT re-emit the captured copy on stderr.
    assert "Invalid page range format" not in captured.err
    assert "usage: [options]" not in captured.err
    # Meta info is still surfaced.
    assert "Error running opendataloader-pdf CLI." in captured.err
    assert "Return code: 2" in captured.err


def test_quiet_success_relays_stdout_but_not_stderr(monkeypatch, capsys, patched_jar):
    """Quiet mode must relay the JAR's stdout (--to-stdout payload, folder
    summary line) to the caller while still suppressing the JAR's log
    stream (stderr). Regression: --quiet + --to-stdout produced no output,
    breaking pipe consumers."""
    result = subprocess.CompletedProcess(
        args=["java", "-jar", "fake.jar"],
        returncode=0,
        stdout="extracted text payload\n",
        stderr="[INFO] java log noise\n",
    )
    monkeypatch.setattr(runner.subprocess, "run", MagicMock(return_value=result))

    returned = runner.run_jar(["doc.pdf", "--quiet", "--to-stdout"], quiet=True)

    captured = capsys.readouterr()
    # Payload reaches the caller's stdout exactly once.
    assert captured.out.count("extracted text payload") == 1
    # The JAR's log stream stays suppressed (that is what quiet means).
    assert "java log noise" not in captured.out
    assert "java log noise" not in captured.err
    # Return value is unchanged for library callers.
    assert returned == "extracted text payload\n"


def test_quiet_relays_through_stdout_buffer_byte_path(monkeypatch, patched_jar):
    """The production CLI writes the relayed payload through
    ``sys.stdout.buffer`` (bytes), not the ``sys.stdout.write`` (str)
    fallback. ``capsys`` replaces ``sys.stdout`` with an object whose
    ``.buffer`` semantics differ, so the sibling test only exercises the
    str branch. This drives the real byte-relay path and asserts the
    payload is encoded to UTF-8 and written exactly once, intact — covering
    the ``encode("utf-8", "replace")`` step that the str branch skips.
    """
    payload = "héllo 한글 payload\n"  # non-ASCII: exercises the utf-8 encode
    result = subprocess.CompletedProcess(
        args=["java", "-jar", "fake.jar"],
        returncode=0,
        stdout=payload,
        stderr="[INFO] java log noise\n",
    )
    monkeypatch.setattr(runner.subprocess, "run", MagicMock(return_value=result))

    # Fake stdout with a real binary buffer, mirroring a genuine TextIOWrapper
    # (hasattr(sys.stdout, "buffer") is True), so run_jar takes the byte path.
    fake_stdout = MagicMock()
    fake_stdout.buffer = io.BytesIO()
    monkeypatch.setattr(runner.sys, "stdout", fake_stdout)

    returned = runner.run_jar(["doc.pdf", "--quiet", "--to-stdout"], quiet=True)

    written = fake_stdout.buffer.getvalue()
    # Byte path was taken: payload written as UTF-8 exactly once, intact.
    assert written == payload.encode("utf-8")
    assert written.decode("utf-8") == payload
    # The str fallback branch was NOT used.
    fake_stdout.write.assert_not_called()
    fake_stdout.buffer.flush()  # buffer is flushed by run_jar; no error
    # Return value is unchanged for library callers.
    assert returned == payload


def test_quiet_failure_prints_captured_streams_once(monkeypatch, capsys, patched_jar):
    """Quiet mode captures output, so the except handler surfaces it — but
    must avoid the old bug where Output and Stdout (aliases) both printed."""
    error = subprocess.CalledProcessError(
        returncode=2,
        cmd=["java", "-jar", "fake.jar"],
        output="captured stdout text",
        stderr="captured stderr text",
    )
    monkeypatch.setattr(runner.subprocess, "run", MagicMock(side_effect=error))

    with pytest.raises(subprocess.CalledProcessError):
        runner.run_jar(["--bogus"], quiet=True)

    err = capsys.readouterr().err
    assert err.count("captured stdout text") == 1
    assert err.count("captured stderr text") == 1
    # The pre-fix code printed both "Output:" and "Stdout:" with the same text.
    assert "Output:" not in err
    assert "Stdout: captured stdout text" in err
    assert "Stderr: captured stderr text" in err
    assert "Error running opendataloader-pdf CLI." in err
    assert "Return code: 2" in err


def test_quiet_forwards_timeout_to_subprocess_run(monkeypatch, patched_jar):
    """The bound must reach ``subprocess.run``, which is what actually kills
    the JVM. Default stays ``None`` so existing callers wait exactly as
    before."""
    result = subprocess.CompletedProcess(
        args=["java", "-jar", "fake.jar"], returncode=0, stdout="", stderr=""
    )
    fake_run = MagicMock(return_value=result)
    monkeypatch.setattr(runner.subprocess, "run", fake_run)

    runner.run_jar(["doc.pdf"], quiet=True, timeout=12.5)
    assert fake_run.call_args.kwargs["timeout"] == 12.5

    runner.run_jar(["doc.pdf"], quiet=True)
    assert fake_run.call_args.kwargs["timeout"] is None


def test_quiet_timeout_is_reported_and_reraised(monkeypatch, capsys, patched_jar):
    """A timeout must be distinguishable from a crash in the caller's logs,
    and must propagate: swallowing it would report a truncated conversion as
    a successful one."""
    monkeypatch.setattr(
        runner.subprocess,
        "run",
        MagicMock(side_effect=subprocess.TimeoutExpired(cmd=["java"], timeout=3)),
    )

    with pytest.raises(subprocess.TimeoutExpired):
        runner.run_jar(["doc.pdf"], quiet=True, timeout=3)

    err = capsys.readouterr().err
    assert "timed out after 3s" in err
    # Not misreported as a non-zero exit.
    assert "Error running opendataloader-pdf CLI." not in err


def test_streaming_timeout_kills_the_jvm(monkeypatch, patched_jar):
    """Streaming mode blocks on the pipe, so the timeout is applied to the
    process and the relay runs on a helper thread: a JVM that stops emitting
    lines is still killed rather than waited on forever."""
    fake_process = MagicMock()
    fake_process.stdout = iter(["[INFO] parsing page 1\n"])
    fake_process.wait.side_effect = [
        subprocess.TimeoutExpired(cmd=["java"], timeout=5),
        0,
    ]
    fake_process.__enter__ = lambda self: self
    fake_process.__exit__ = lambda self, *_a: False
    monkeypatch.setattr(runner.subprocess, "Popen", lambda *_a, **_kw: fake_process)

    with pytest.raises(subprocess.TimeoutExpired) as excinfo:
        runner.run_jar(["doc.pdf"], quiet=False, timeout=5)

    # The JVM is killed, not left running behind a raised exception.
    fake_process.kill.assert_called_once()
    # Whatever the JAR had already emitted is attached, not discarded.
    assert "parsing page 1" in (excinfo.value.output or "")


def test_streaming_without_timeout_keeps_the_inline_read(monkeypatch, patched_jar):
    """Default behaviour is unchanged: no helper thread, and ``wait()`` is
    called without a bound."""
    fake_process = MagicMock()
    fake_process.stdout = iter(["line one\n", "line two\n"])
    fake_process.wait.return_value = 0
    fake_process.__enter__ = lambda self: self
    fake_process.__exit__ = lambda self, *_a: False
    monkeypatch.setattr(runner.subprocess, "Popen", lambda *_a, **_kw: fake_process)

    no_threads = MagicMock()
    monkeypatch.setattr(runner.threading, "Thread", no_threads)

    returned = runner.run_jar(["doc.pdf"], quiet=False)

    assert returned == "line one\nline two\n"
    no_threads.assert_not_called()
    fake_process.wait.assert_called_once_with()


def test_streaming_success_waits_for_the_full_relay(monkeypatch, patched_jar):
    """A bounded run that SUCCEEDS must still return everything the JAR wrote.

    The JVM can exit while the pipe still holds buffered output, and relaying it
    onward can outlast the relay-join bound when the parent's stdout is slow.
    Cutting the join short there would silently truncate the return value --
    including a ``--to-stdout`` payload -- which is worse than the wait it saves.
    The timeout bounds the JVM; once the JVM is gone the relay runs to EOF.
    """
    def slow_lines():
        yield "first line\n"
        # Outlast the join bound while the process has already exited.
        time.sleep(runner._RELAY_JOIN_TIMEOUT_S + 0.3)
        yield "last line\n"

    fake_process = MagicMock()
    fake_process.stdout = slow_lines()
    fake_process.wait.return_value = 0
    fake_process.__enter__ = lambda self: self
    fake_process.__exit__ = lambda self, *_a: False
    monkeypatch.setattr(runner.subprocess, "Popen", lambda *_a, **_kw: fake_process)

    returned = runner.run_jar(["doc.pdf"], quiet=False, timeout=30)

    # Nothing may be dropped: the tail arrived after the join bound elapsed.
    assert returned == "first line\nlast line\n"
