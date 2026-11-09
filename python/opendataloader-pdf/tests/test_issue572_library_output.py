"""The library API returns the JAR output; only the bundled CLI writes it to the console.

Issue #572: run_jar both returned the JAR's stdout and wrote it to the console,
and convert() used it, so a library call wrote to the caller's console and
returned None. convert() now returns the JAR's stdout and writes nothing on
success, with or without quiet, as convert() of the Node package does. The
bundled CLI and the deprecated run() keep their console output.

A java stand-in on PATH writes one log line to stderr and one result line to
stdout, as the JAR does. capfd checks the file descriptors, so a write by the
child process to the console is found too.
"""

import os
import signal
import subprocess
import sys
import textwrap
import time
from unittest.mock import MagicMock

import pytest

from opendataloader_pdf import convert, runner, wrapper

pytestmark = pytest.mark.skipif(
    sys.platform == "win32", reason="the java stand-in is a POSIX shell script"
)

LOG = "log line of the stand-in"
RESULT = "result line of the stand-in"

JAVA = textwrap.dedent(f"""\
    #!/bin/sh
    echo '{LOG}' >&2
    echo '{RESULT}'
    exit "${{ODL_TEST_EXIT_CODE:-0}}"
    """)


class _FakeAsFile:
    def __init__(self, path):
        self._path = path

    def __enter__(self):
        return self._path

    def __exit__(self, *_args):
        return False


@pytest.fixture
def stand_in(monkeypatch, tmp_path):
    """Put the java stand-in first on PATH, and skip the lookup of the bundled JAR."""
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    java = bin_dir / "java"
    java.write_text(JAVA)
    java.chmod(0o755)
    monkeypatch.setenv("PATH", os.pathsep.join([str(bin_dir), os.environ["PATH"]]))
    monkeypatch.delenv("ODL_TEST_EXIT_CODE", raising=False)
    fake_jar = tmp_path / "opendataloader-pdf-cli.jar"
    fake_jar.write_bytes(b"")
    fake_files = MagicMock()
    fake_files.joinpath = lambda *_a, **_kw: fake_jar
    monkeypatch.setattr(runner.resources, "files", lambda _pkg: fake_files)
    monkeypatch.setattr(runner.resources, "as_file", lambda p: _FakeAsFile(p))


@pytest.mark.parametrize("quiet", [True, False])
def test_convert_returns_the_jar_stdout(stand_in, capfd, quiet):
    returned = convert(input_path="doc.pdf", quiet=quiet)
    capfd.readouterr()
    assert (
        returned == RESULT + "\n"
    ), f"convert() did not return the JAR's stdout (quiet={quiet}): {returned!r}"


@pytest.mark.parametrize("quiet", [True, False])
def test_convert_writes_nothing_to_the_console(stand_in, capfd, quiet):
    convert(input_path="doc.pdf", quiet=quiet)
    captured = capfd.readouterr()
    assert (captured.out, captured.err) == ("", ""), (
        f"convert() wrote to the console (quiet={quiet}): "
        f"stdout={captured.out!r} stderr={captured.err!r}"
    )


def test_cli_with_quiet_prints_the_jar_stdout_once(stand_in, capfd):
    assert wrapper.main(["doc.pdf", "--quiet"]) == 0
    captured = capfd.readouterr()
    assert captured.out == RESULT + "\n"
    assert captured.err == ""


def test_cli_without_quiet_prints_the_jar_output_once(stand_in, capfd):
    assert wrapper.main(["doc.pdf"]) == 0
    captured = capfd.readouterr()
    assert captured.out.count(RESULT) == 1
    assert captured.out.count(LOG) == 1


@pytest.mark.parametrize("quiet", [True, False])
def test_cli_returns_the_exit_code_of_the_jar(stand_in, capfd, monkeypatch, quiet):
    monkeypatch.setenv("ODL_TEST_EXIT_CODE", "3")
    argv = ["doc.pdf", "--quiet"] if quiet else ["doc.pdf"]
    assert wrapper.main(argv) == 3
    assert "Error running opendataloader-pdf CLI." in capfd.readouterr().err


def test_deprecated_run_with_debug_prints_the_jar_output(stand_in, capfd):
    with pytest.warns(DeprecationWarning):
        wrapper.run("doc.pdf", debug=True)
    captured = capfd.readouterr()
    assert captured.out.count(RESULT) == 1
    assert captured.out.count(LOG) == 1


@pytest.mark.parametrize("quiet", [True, False])
def test_convert_failure_raises_with_the_jar_stdout(stand_in, capfd, monkeypatch, quiet):
    monkeypatch.setenv("ODL_TEST_EXIT_CODE", "3")
    with pytest.raises(subprocess.CalledProcessError) as raised:
        convert(input_path="doc.pdf", quiet=quiet)
    assert raised.value.returncode == 3
    assert RESULT in raised.value.output
    assert "Error running opendataloader-pdf CLI." in capfd.readouterr().err


WAITING_JAVA = '#!/bin/sh\n: > "$ODL_TEST_READY"\nexec sleep 60\n'

CHILD = textwrap.dedent("""\
    import contextlib, os, sys
    from opendataloader_pdf import convert, runner
    jar = os.path.join(os.environ["ODL_TEST_JAR_DIR"], "opendataloader-pdf-cli.jar")
    open(jar, "wb").close()
    runner.resources.files = lambda _pkg: type("Files", (), {"joinpath": lambda self, *_a: jar})()
    runner.resources.as_file = contextlib.nullcontext
    convert(input_path="doc.pdf", timeout=float(sys.argv[1]))
    """)


@pytest.mark.parametrize("timeout", ["60", "inf"])
def test_an_interrupt_ends_convert_and_the_jvm(tmp_path, timeout):
    """A JVM that prints nothing and waits: an interrupt of the Python process
    ends convert() at once, and the JVM does not stay."""
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    java = bin_dir / "java"
    java.write_text(WAITING_JAVA)
    java.chmod(0o755)
    ready = tmp_path / "ready"
    env = dict(os.environ, ODL_TEST_JAR_DIR=str(tmp_path), ODL_TEST_READY=str(ready))
    env["PATH"] = os.pathsep.join([str(bin_dir), env["PATH"]])

    child = subprocess.Popen(
        [sys.executable, "-c", CHILD, timeout],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        start_new_session=True,
    )
    try:
        deadline = time.monotonic() + 10
        while not ready.exists():
            if child.poll() is not None or time.monotonic() > deadline:
                pytest.fail(
                    "the java stand-in did not start: "
                    + child.stderr.read().decode(errors="replace")
                )
            time.sleep(0.05)
        os.kill(child.pid, signal.SIGINT)  # the Python process only, not the stand-in
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pytest.fail(f"convert(timeout={timeout}) did not end within 5 s after SIGINT")
        assert b"KeyboardInterrupt" in child.stderr.read()
        with pytest.raises(ProcessLookupError):
            os.killpg(child.pid, 0)  # nothing is left in the group of the child
    finally:
        try:
            os.killpg(child.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        child.wait()
