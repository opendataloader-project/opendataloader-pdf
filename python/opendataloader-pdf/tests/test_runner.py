"""Unit tests for runner.py error-handling behaviour.

Regression: when the JAR fails, the streaming branch already wrote the
JAR's stdout to the console live, so the except handler must not re-emit
the captured copy. The quiet branch, conversely, has not surfaced anything
yet and is allowed to print the captured streams — but only once
(``CalledProcessError.output`` and ``.stdout`` are the same attribute).
"""

import io
import os
import subprocess
import tempfile
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


_JAVA_OPTION_VARS = ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")


def _capture_command(monkeypatch):
    """Record the command that run_jar passes to subprocess.run."""
    seen = {}

    def fake_run(command, **_kwargs):
        seen["command"] = command
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(runner.subprocess, "run", fake_run)
    return seen


@pytest.fixture
def no_java_options(monkeypatch):
    for name in _JAVA_OPTION_VARS:
        monkeypatch.delenv(name, raising=False)


def test_passes_runtime_temp_dir_to_java(monkeypatch, patched_jar, no_java_options):
    """The JVM does not read TMPDIR. Java must use the temporary folder of this
    Python runtime, so that a writable TMPDIR is enough (#785)."""
    seen = _capture_command(monkeypatch)

    runner.run_jar(["input.pdf"], quiet=True)

    command = seen["command"]
    option = f"-Djava.io.tmpdir={tempfile.gettempdir()}"
    assert option in command, command
    assert command.index(option) < command.index("-jar"), command


@pytest.mark.parametrize("name", _JAVA_OPTION_VARS)
def test_keeps_user_java_tmpdir(monkeypatch, patched_jar, no_java_options, name):
    """A command-line option wins over JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS.
    If the user set java.io.tmpdir there, the runner must not override it."""
    monkeypatch.setenv(name, "-Xmx1g -Djava.io.tmpdir=/user/choice")
    seen = _capture_command(monkeypatch)

    runner.run_jar(["input.pdf"], quiet=True)

    assert not any(arg.startswith("-Djava.io.tmpdir=") for arg in seen["command"]), seen["command"]


# --------------------------------------------------------------------------- #
# Non-ASCII path handling
#
# On Windows the Java launcher decodes argv with the system ANSI code page, so
# non-ASCII paths become "?" and the JAR cannot find the file. The runner swaps
# such paths for their ASCII short (8.3) form. It is a no-op on POSIX and for
# ASCII arguments.
# --------------------------------------------------------------------------- #
@pytest.fixture
def fake_short_paths(monkeypatch):
    """Give every existing path a short form, as Windows does, on any OS."""
    shorts = {}

    def fake_short_path(path):
        if not os.path.exists(path):
            return None
        return shorts.setdefault(path, f"SHORT~{len(shorts) + 1}")

    monkeypatch.setattr(runner, "_short_path", fake_short_path)
    return shorts


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


def test_make_jvm_safe_args_shortens_existing_paths_only(tmp_path, fake_short_paths):
    """An existing non-ASCII path is shortened. A non-ASCII value that is not a
    path, such as a password, is left as it is."""
    greek = tmp_path / "έγγραφο.pdf"
    greek.write_bytes(b"%PDF-1.4")

    result = runner._make_jvm_safe_args([str(greek), "--password", "κωδικός"])

    assert result == [fake_short_paths[str(greek)], "--password", "κωδικός"]


@pytest.mark.parametrize("option", ["--output-dir", "-o", "--image-dir"])
def test_make_jvm_safe_args_creates_and_shortens_output_dir(tmp_path, fake_short_paths, option):
    """A missing non-ASCII output folder is created, so that it has a short
    form, in both the "--option value" and "--option=value" forms."""
    out = tmp_path / "έξοδος"

    separate = runner._make_jvm_safe_args(["in.pdf", option, str(out)])
    joined = runner._make_jvm_safe_args([f"{option}={out}"])

    assert out.is_dir()
    short = fake_short_paths[str(out)]
    assert separate == ["in.pdf", option, short]
    assert joined == [f"{option}={short}"]


def test_make_jvm_safe_args_accepts_path_objects(tmp_path, fake_short_paths):
    """subprocess accepts pathlib.Path arguments, so callers can pass them."""
    greek = tmp_path / "έγγραφο.pdf"
    greek.write_bytes(b"%PDF-1.4")

    result = runner._make_jvm_safe_args([greek, "--output-dir", tmp_path / "out"])

    assert result == [fake_short_paths[str(greek)], "--output-dir", str(tmp_path / "out")]


def test_ascii_path_keeps_path_without_ascii_short_form(tmp_path, monkeypatch):
    """If the volume creates no short names, Windows returns the long path.
    Keep the original path then."""
    greek = tmp_path / "έγγραφο.pdf"
    greek.write_bytes(b"%PDF-1.4")
    monkeypatch.setattr(runner, "_short_path", lambda path: path)

    assert runner._ascii_path(str(greek)) == str(greek)


def test_passes_ascii_temp_dir_to_java(monkeypatch, tmp_path, patched_jar, no_java_options, fake_short_paths):
    """A user name with non-ASCII characters puts the temporary folder on a
    non-ASCII path. Java must get its short form."""
    temp_dir = tmp_path / "Στράτος"
    temp_dir.mkdir()
    monkeypatch.setattr(runner.tempfile, "gettempdir", lambda: str(temp_dir))
    seen = _capture_command(monkeypatch)

    runner.run_jar(["input.pdf"], quiet=True)

    assert f"-Djava.io.tmpdir={fake_short_paths[str(temp_dir)]}" in seen["command"]


def test_passes_ascii_jar_path_to_java(monkeypatch, tmp_path, no_java_options, fake_short_paths):
    """A virtual environment in a non-ASCII user folder puts the JAR on a
    non-ASCII path. Java must get its short form."""
    jar = tmp_path / "Στράτος" / "opendataloader-pdf-cli.jar"
    jar.parent.mkdir()
    jar.write_bytes(b"")
    traversable = MagicMock()
    traversable.joinpath = lambda *_a, **_kw: jar
    monkeypatch.setattr(runner.resources, "files", lambda _pkg: traversable)
    monkeypatch.setattr(runner.resources, "as_file", lambda p: _FakeAsFile(p))
    seen = _capture_command(monkeypatch)

    runner.run_jar(["input.pdf"], quiet=True)

    command = seen["command"]
    assert command[command.index("-jar") + 1] == fake_short_paths[str(jar)]


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
