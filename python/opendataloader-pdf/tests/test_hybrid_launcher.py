"""Tests for HybridServer: a hybrid server that Python starts and stops (#440).

A small stub program takes the place of the docling server, so the tests load
no models. Like the real server, it opens its port only after its startup and
answers /health and /v1/convert/file. It writes its arguments and each convert
request to a record file.
"""

import _thread
import json
import socket
import subprocess
import sys
import threading
import time
import urllib.request

import pytest

import opendataloader_pdf

SYMPTOM = "the hybrid server must be started by hand: opendataloader_pdf has no HybridServer"

STUB_MODULE = "stub_hybrid_server"

STUB = r"""
import argparse
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser()
parser.add_argument("--record", required=True)
parser.add_argument("--load-seconds", type=float, default=0.3)
parser.add_argument("--exit-code", type=int)
parser.add_argument("--never-ready", action="store_true")
parser.add_argument("--host", required=True)
parser.add_argument("--port", type=int, required=True)
args = parser.parse_args()


def record(event):
    with open(args.record, "a", encoding="utf-8") as f:
        f.write(json.dumps(event) + "\n")


record({"event": "start", "pid": os.getpid(), "argv": sys.argv[1:]})
print("stub log line", flush=True)
if args.exit_code is not None:
    sys.exit(args.exit_code)
# Like the model load of the real server: no port until it ends
time.sleep(3600 if args.never_ready else args.load_seconds)

CONVERT_RESPONSE = json.dumps({
    "status": "success",
    "document": {"json_content": {"texts": [], "pages": {}}},
    "processing_time": 0.1,
    "errors": [],
    "failed_pages": [],
}).encode()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.send(b'{"status": "ok"}')
        else:
            self.send_error(404)

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        record({"event": "request", "path": self.path})
        self.send(CONVERT_RESPONSE)

    def send(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
"""


def hybrid_server_class():
    server_class = getattr(opendataloader_pdf, "HybridServer", None)
    if server_class is None:
        pytest.fail(SYMPTOM)
    return server_class


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def health(url):
    """The status of GET <url>/health, or None if nothing answers."""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(url + "/health", timeout=2) as response:
            return response.status
    except OSError:
        return None


def events(record, name):
    if not record.exists():
        return []
    lines = record.read_text(encoding="utf-8").splitlines()
    return [e for e in map(json.loads, lines) if e["event"] == name]


@pytest.fixture
def stub(tmp_path, monkeypatch):
    """Puts the stub in place of the docling server. Returns its record file."""
    (tmp_path / f"{STUB_MODULE}.py").write_text(STUB, encoding="utf-8")
    monkeypatch.setenv("PYTHONPATH", str(tmp_path))
    server_class = getattr(opendataloader_pdf, "HybridServer", None)
    if server_class is not None:
        monkeypatch.setattr(sys.modules[server_class.__module__], "_SERVER_MODULE", STUB_MODULE)
    return tmp_path / "record.jsonl"


@pytest.fixture
def started(monkeypatch):
    """The processes that the test starts."""
    processes = []
    real_popen = subprocess.Popen

    def popen(*args, **kwargs):
        processes.append(real_popen(*args, **kwargs))
        return processes[-1]

    monkeypatch.setattr(subprocess, "Popen", popen)
    return processes


def test_server_answers_inside_the_block_and_stops_after(stub, started):
    HybridServer = hybrid_server_class()

    with HybridServer(args=["--record", str(stub)]) as server:
        assert health(server.url) == 200

    assert health(server.url) is None
    assert [p.poll() is not None for p in started] == [True]


def test_convert_calls_in_one_block_share_one_server(stub, input_pdf, tmp_path):
    HybridServer = hybrid_server_class()

    with HybridServer(args=["--record", str(stub)]) as server:
        for name in ("first", "second"):
            opendataloader_pdf.convert(
                input_path=str(input_pdf),
                output_dir=str(tmp_path / name),
                format="json",
                pages="1",
                hybrid="docling-fast",
                hybrid_mode="full",
                hybrid_url=server.url,
                quiet=True,
            )

    assert (tmp_path / "first" / f"{input_pdf.stem}.json").exists()
    assert (tmp_path / "second" / f"{input_pdf.stem}.json").exists()
    assert len(events(stub, "start")) == 1
    assert [e["path"] for e in events(stub, "request")] == ["/v1/convert/file"] * 2


def test_server_listens_on_loopback_at_the_given_port_with_the_args(stub):
    HybridServer = hybrid_server_class()
    port = free_port()
    args = ["--record", str(stub), "--load-seconds", "0"]

    with HybridServer(args=args, port=port) as server:
        assert server.url == f"http://127.0.0.1:{port}"
        assert health(server.url) == 200

    [start] = events(stub, "start")
    assert start["argv"] == [*args, "--host", "127.0.0.1", "--port", str(port)]


def test_server_log_goes_to_stderr(stub, capfd):
    HybridServer = hybrid_server_class()

    with HybridServer(args=["--record", str(stub)]):
        pass

    out, err = capfd.readouterr()
    assert "stub log line" not in out
    assert "stub log line" in err


def test_server_that_exits_during_startup_raises(stub):
    HybridServer = hybrid_server_class()

    with pytest.raises(RuntimeError, match="exited with code 3"):
        with HybridServer(args=["--record", str(stub), "--exit-code", "3"]):
            pass


def test_server_that_does_not_answer_in_time_is_stopped(stub, started):
    HybridServer = hybrid_server_class()

    with pytest.raises(TimeoutError):
        with HybridServer(args=["--record", str(stub), "--never-ready"], startup_timeout=1):
            pass

    assert [p.poll() is not None for p in started] == [True]


def test_server_stops_when_the_block_raises(stub, started):
    HybridServer = hybrid_server_class()

    with pytest.raises(ValueError, match="in the block"):
        with HybridServer(args=["--record", str(stub)]) as server:
            raise ValueError("in the block")

    assert health(server.url) is None
    assert [p.poll() is not None for p in started] == [True]


def test_interrupt_during_startup_stops_the_server(stub, started):
    HybridServer = hybrid_server_class()
    timer = threading.Timer(1, _thread.interrupt_main)

    timer.start()
    try:
        with pytest.raises(KeyboardInterrupt):
            with HybridServer(args=["--record", str(stub), "--never-ready"], startup_timeout=30):
                pass
    finally:
        timer.cancel()

    assert [p.poll() is not None for p in started] == [True]


@pytest.mark.parametrize("args", [["--host", "0.0.0.0"], ["--port=5002"]])
def test_args_cannot_set_host_or_port(stub, started, args):
    HybridServer = hybrid_server_class()

    with pytest.raises(ValueError, match="--host or --port"):
        HybridServer(args=args)

    assert started == []


def test_port_in_use_raises(stub, started):
    """Another server on the port would answer the health check before ours opens it."""
    HybridServer = hybrid_server_class()

    with socket.socket() as other:
        other.bind(("127.0.0.1", 0))
        other.listen()
        port = other.getsockname()[1]
        with pytest.raises(OSError, match="in use"):
            HybridServer(args=["--record", str(stub)], port=port).start()

    assert started == []


def test_import_loads_no_hybrid_dependency():
    """The package has no dependencies, so an import must not need the hybrid extra."""
    code = (
        "import sys, opendataloader_pdf; "
        "print(sorted({'docling', 'fastapi', 'uvicorn', 'torch'} & set(sys.modules)))"
    )
    result = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "[]"


def test_convert_uses_a_server_started_by_hand(stub, input_pdf, tmp_path):
    port = free_port()
    url = f"http://127.0.0.1:{port}"
    command = [sys.executable, "-m", STUB_MODULE, "--record", str(stub)]
    process = subprocess.Popen([*command, "--host", "127.0.0.1", "--port", str(port)])
    try:
        deadline = time.monotonic() + 30
        while health(url) != 200:
            assert process.poll() is None and time.monotonic() < deadline, "the stub did not start"
            time.sleep(0.1)
        opendataloader_pdf.convert(
            input_path=str(input_pdf),
            output_dir=str(tmp_path),
            format="json",
            pages="1",
            hybrid="docling-fast",
            hybrid_mode="full",
            hybrid_url=url,
            quiet=True,
        )
    finally:
        process.terminate()
        process.wait()

    assert (tmp_path / f"{input_pdf.stem}.json").exists()
    assert [e["path"] for e in events(stub, "request")] == ["/v1/convert/file"]
