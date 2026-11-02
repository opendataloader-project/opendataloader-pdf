"""Acceptance test for #683 (review round 2): no process outlives the process that was started.

A program that runs the hybrid server and later ends it, with Popen.terminate(), Task Manager, or
taskkill /PID without /T, ends one process. Windows does not end the child processes of that
process. On main, that process is the server, so the server stops. This test checks that no
process that the start created is left after that end.

It runs only on Windows with a code page that is not UTF-8 (for example cp949 or cp1252). It needs
no backend and no network. A sitecustomize.py on PYTHONPATH makes each Python process that runs in
UTF-8 mode write its process id and wait, like a server that still runs.
"""

import locale
import os
import signal
import subprocess
import sys
import time

import pytest

pytestmark = pytest.mark.skipif(
    sys.platform != "win32" or "utf" in locale.getpreferredencoding(False).lower(),
    reason="needs Windows with a code page that is not UTF-8",
)

SITECUSTOMIZE = """\
import os
import sys
import time

if sys.flags.utf8_mode:
    with open(os.environ["REVIEW683_PIDS"], "a", encoding="ascii") as record:
        record.write(f"{os.getpid()}\\n")
    time.sleep(60)
"""

# The call that the opendataloader-pdf-hybrid command makes: main() with its arguments
START = [
    sys.executable,
    "-c",
    "import sys; from opendataloader_pdf.hybrid_server import main; "
    "sys.argv = ['opendataloader-pdf-hybrid', '--help']; main()",
]


def _alive(pid):
    if sys.platform == "win32":
        tasks = subprocess.run(
            ["tasklist", "/FI", f"PID eq {pid}", "/NH"],
            capture_output=True,
            text=True,
            errors="replace",
        ).stdout
        return str(pid) in tasks.split()
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def _end(pid):
    if sys.platform == "win32":
        subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True)
    else:
        os.kill(pid, signal.SIGKILL)


def test_no_process_is_left_after_the_started_process_ends(tmp_path):
    probe = tmp_path / "probe"
    probe.mkdir()
    (probe / "sitecustomize.py").write_text(SITECUSTOMIZE, encoding="utf-8")
    pids = tmp_path / "pids.txt"
    # The started process is not in UTF-8 mode; -X utf8 of a child still turns the mode on
    env = dict(os.environ, PYTHONUTF8="0")
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, "src")
    env["PYTHONPATH"] = os.pathsep.join([str(probe), os.path.abspath(src)])
    env["REVIEW683_PIDS"] = str(pids)

    started = subprocess.Popen(START, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline and started.poll() is None and not pids.exists():
        time.sleep(0.2)
    if not pids.exists():
        # No process started in UTF-8 mode, so no such process can be left
        started.wait(timeout=60)
        return

    time.sleep(0.5)
    started.terminate()  # On Windows: TerminateProcess, which ends this process only
    started.wait(timeout=30)
    time.sleep(2)
    left = [int(p) for p in pids.read_text(encoding="ascii").split() if _alive(int(p))]
    for pid in left:
        _end(pid)
    assert left == [], f"processes left after the started process ended: {left}"
