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
