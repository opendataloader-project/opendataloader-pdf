"""Unit tests for runner.py error-handling behaviour.

Regression: when the JAR fails, the streaming branch already wrote the
JAR's stdout to the console live, so the except handler must not re-emit
the captured copy. The quiet branch, conversely, has not surfaced anything
yet and is allowed to print the captured streams — but only once
(``CalledProcessError.output`` and ``.stdout`` are the same attribute).
"""

import subprocess
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


def test_quiet_timeout_forwards_to_subprocess_run(monkeypatch, patched_jar):
    """Quiet mode delegates to subprocess.run; the timeout kwarg must be
    forwarded so a hung JVM is SIGKILLed and TimeoutExpired surfaces to
    the caller. Without this, a long-running service that imports
    opendataloader-pdf has no bound on the JVM and a pathological PDF can
    stall the whole process indefinitely."""
    seen_kwargs: dict = {}

    def fake_run(*args, **kwargs):
        seen_kwargs.update(kwargs)
        # Simulate the expiry that subprocess.run produces.
        raise subprocess.TimeoutExpired(args[0], timeout=kwargs.get("timeout"))

    monkeypatch.setattr(runner.subprocess, "run", fake_run)

    with pytest.raises(subprocess.TimeoutExpired) as excinfo:
        runner.run_jar(["--quiet-some-input"], quiet=True, timeout=0.5)

    assert seen_kwargs.get("timeout") == 0.5
    assert excinfo.value.timeout == 0.5


def test_streaming_timeout_kills_process(monkeypatch, patched_jar):
    """Streaming mode tracks the deadline itself (subprocess.run's
    ``timeout`` doesn't work while we're tee-ing stdout). On expiry we
    must call ``process.kill()`` and raise TimeoutExpired, matching the
    shape of the quiet-mode path."""
    long_output = ["chunk\n" for _ in range(100)]
    fake_process = MagicMock()
    fake_process.stdout = iter(long_output)
    fake_process.__enter__ = lambda self: self
    fake_process.__exit__ = lambda self, *_a: False

    monkeypatch.setattr(runner.subprocess, "Popen", lambda *_a, **_kw: fake_process)

    # Inject a fake time source that advances faster than the loop reads
    # lines, so the deadline expires on the first iteration.
    import itertools
    t = itertools.count(start=0, step=10)
    monkeypatch.setattr(runner, "_time" if False else "subprocess",
                        runner.subprocess)  # keep import shape
    import time as _real_time
    monkeypatch.setattr(_real_time, "monotonic", lambda: next(t))

    with pytest.raises(subprocess.TimeoutExpired):
        runner.run_jar(["--something"], quiet=False, timeout=1.0)

    fake_process.kill.assert_called()


def test_quiet_no_timeout_preserves_legacy_behavior(monkeypatch, patched_jar):
    """When ``timeout`` is not supplied (default), subprocess.run still
    receives ``timeout=None`` — matching its own no-bound default.
    Verifies the kwarg always flows through and we never accidentally
    drop it."""
    seen_kwargs: dict = {}

    def fake_run(*args, **kwargs):
        seen_kwargs.update(kwargs)
        result = MagicMock()
        result.stdout = "ok"
        return result

    monkeypatch.setattr(runner.subprocess, "run", fake_run)

    out = runner.run_jar(["--ok"], quiet=True)
    assert out == "ok"
    assert "timeout" in seen_kwargs
    assert seen_kwargs["timeout"] is None


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
