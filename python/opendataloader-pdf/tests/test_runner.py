"""Unit tests for runner.py error-handling behaviour.

Regression: when the JAR fails, the streaming branch already wrote the
JAR's stdout to the console live, so the except handler must not re-emit
the captured copy. The quiet branch, conversely, has not surfaced anything
yet and is allowed to print the captured streams — but only once
(``CalledProcessError.output`` and ``.stdout`` are the same attribute).
"""

import os
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


# --------------------------------------------------------------------------- #
# Non-ASCII path handling (_make_jvm_safe_args)
#
# On Windows the Java launcher decodes argv with the system ANSI code page, so
# non-ASCII paths become "?" and the JAR cannot find the file. The shim swaps
# such paths for their ASCII short (8.3) form. It is a no-op on POSIX and for
# ASCII arguments.
# --------------------------------------------------------------------------- #
def test_make_jvm_safe_args_passthrough_ascii():
    """ASCII arguments are never altered, on any platform."""
    args = ["input.pdf", "--format", "markdown", "--output-dir", "out"]
    assert runner._make_jvm_safe_args(args) == args


@pytest.mark.skipif(os.name == "nt", reason="POSIX no-op behaviour")
def test_make_jvm_safe_args_noop_on_posix(tmp_path):
    """On POSIX the JVM decodes argv as UTF-8, so non-ASCII paths are left
    untouched."""
    greek = tmp_path / "έγγραφο.pdf"
    greek.write_bytes(b"%PDF-1.4")
    assert runner._make_jvm_safe_args([str(greek)]) == [str(greek)]


@pytest.mark.skipif(os.name != "nt", reason="Windows short-path behaviour")
def test_make_jvm_safe_args_shortens_non_ascii_input_on_windows(tmp_path):
    """A non-ASCII input path is replaced by an ASCII short path pointing at
    the same file."""
    greek = tmp_path / "έγγραφο.pdf"
    greek.write_bytes(b"%PDF-1.4")
    result = runner._make_jvm_safe_args([str(greek)])
    assert result[0].isascii()
    assert os.path.samefile(result[0], str(greek))


@pytest.mark.skipif(os.name != "nt", reason="Windows short-path behaviour")
def test_make_jvm_safe_args_creates_and_shortens_output_dir_on_windows(tmp_path):
    """A non-ASCII --output-dir is created (so a short name exists) and
    rewritten to an ASCII short path; surrounding tokens are preserved."""
    out = tmp_path / "έξοδος"
    result = runner._make_jvm_safe_args(["in.pdf", "--output-dir", str(out)])
    assert result[0] == "in.pdf"
    assert result[1] == "--output-dir"
    assert result[2].isascii()
    assert os.path.isdir(out)
    assert os.path.samefile(result[2], str(out))
