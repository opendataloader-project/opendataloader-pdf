"""An interrupt ends a bounded streaming run at once.

Without a timeout, a KeyboardInterrupt in run_jar(quiet=False) ends the call at once. With a
timeout, it must do the same, also while the JVM prints nothing: the caller set the bound so that
the call does not hang. A java stand-in on PATH prints one line and then waits, like a stuck JVM.
The interrupt goes to the Python process only, as from `kill -INT` or a signal handler.
"""

import os
import signal
import subprocess
import sys
import textwrap

import pytest

pytestmark = pytest.mark.skipif(
    sys.platform == "win32", reason="the java stand-in is a POSIX shell script"
)

JAVA = "#!/bin/sh\necho 'stand-in started'\nexec sleep 60\n"

CHILD = textwrap.dedent(
    """\
    import contextlib, os, sys
    from opendataloader_pdf import runner
    jar = os.path.join(os.environ["ODL_TEST_JAR_DIR"], "opendataloader-pdf-cli.jar")
    open(jar, "wb").close()
    runner.resources.files = lambda _pkg: type("Files", (), {"joinpath": lambda self, *_a: jar})()
    runner.resources.as_file = contextlib.nullcontext
    kwargs = {} if sys.argv[1] == "none" else {"timeout": float(sys.argv[1])}
    runner.run_jar(["input.pdf"], quiet=False, **kwargs)
    """
)


@pytest.mark.parametrize("timeout", ["none", "60"])
def test_interrupt_ends_a_streaming_run_at_once(tmp_path, timeout):
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    java = bin_dir / "java"
    java.write_text(JAVA)
    java.chmod(0o755)
    env = dict(os.environ, ODL_TEST_JAR_DIR=str(tmp_path))
    env["PATH"] = os.pathsep.join([str(bin_dir), env["PATH"]])

    child = subprocess.Popen(
        [sys.executable, "-c", CHILD, timeout],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        start_new_session=True,
    )
    try:
        first = child.stdout.readline()
        assert b"stand-in started" in first, child.stderr.read().decode(errors="replace")
        os.kill(child.pid, signal.SIGINT)  # the Python process only, not the stand-in
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pytest.fail(f"run_jar(timeout={timeout}) did not end within 5 s after SIGINT")
        assert b"KeyboardInterrupt" in child.stderr.read()
    finally:
        try:
            os.killpg(child.pid, signal.SIGKILL)  # the stand-in is in the same group
        except ProcessLookupError:
            pass
        child.wait()
