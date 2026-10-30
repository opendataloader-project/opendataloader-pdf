"""
Start and stop a local hybrid server from Python.
"""

import socket
import subprocess
import sys
import time
import urllib.request
from typing import List, Optional, Sequence

# The bundled server (the `opendataloader-pdf-hybrid` command)
_SERVER_MODULE = "opendataloader_pdf.hybrid_server"

# The server accepts connections only from this computer
_HOST = "127.0.0.1"

_POLL_SECONDS = 0.5
_HEALTH_TIMEOUT_SECONDS = 2
_STOP_TIMEOUT_SECONDS = 10

# The health check goes to this computer, never through an HTTP proxy of the environment
_LOCAL_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _free_port() -> int:
    with socket.socket() as s:
        s.bind((_HOST, 0))
        return s.getsockname()[1]


def _in_use(port: int) -> bool:
    try:
        with socket.create_connection((_HOST, port), timeout=1):
            return True
    except OSError:
        return False


def _server_command(port: int, args: Sequence[str]) -> List[str]:
    # Host and port come last: for a repeated option, argparse keeps the last value
    return [sys.executable, "-m", _SERVER_MODULE, *args, "--host", _HOST, "--port", str(port)]


class HybridServer:
    """A local hybrid server that runs for the time of a ``with`` block.

    The server loads its models before it answers. The first start can take
    minutes, because the models download. Use one server for all convert() calls::

        with HybridServer() as server:
            convert(input_path=["a.pdf", "b.pdf"], hybrid="docling-fast", hybrid_url=server.url)

    The server needs the hybrid extra: pip install "opendataloader-pdf[hybrid]".
    Its log goes to stderr.

    Args:
        args: Server options, as for the opendataloader-pdf-hybrid command
            (for example ["--force-ocr", "--ocr-lang", "ko,en"]).
        port: Port on 127.0.0.1. Default: a free port. If something listens on the
            port, start() raises OSError.
        startup_timeout: Seconds to wait until the server answers. Default: 600.
    """

    def __init__(
        self,
        args: Optional[Sequence[str]] = None,
        *,
        port: Optional[int] = None,
        startup_timeout: float = 600.0,
    ):
        if isinstance(args, str):
            raise TypeError("args is a list of arguments, for example ['--no-ocr']")
        self._args = list(args or [])
        for arg in self._args:
            if arg.split("=", 1)[0] in ("--host", "--port"):
                raise ValueError(
                    "args cannot set --host or --port: the server listens on 127.0.0.1, "
                    "and port= sets the port"
                )
        self._requested_port = port
        self._startup_timeout = startup_timeout
        self._process: Optional[subprocess.Popen] = None
        self.port: Optional[int] = None

    @property
    def url(self) -> str:
        """The server URL, for convert(hybrid_url=...)."""
        if self.port is None:
            raise RuntimeError("The hybrid server is not started")
        return f"http://{_HOST}:{self.port}"

    def start(self) -> "HybridServer":
        """Start the server and wait until it answers."""
        if self._process is not None:
            raise RuntimeError("The hybrid server is already started")
        port = self._requested_port or _free_port()
        # The server opens its port only after it loads its models. Until then, a
        # server that already listens there would answer the health check in its place.
        if _in_use(port):
            raise OSError(f"Port {port} on {_HOST} is in use. Give another port, or none")
        self.port = port
        # The server logs to stdout. Send the log to stderr (file descriptor 2),
        # so that it does not mix with the output of convert(to_stdout=True).
        self._process = subprocess.Popen(
            _server_command(self.port, self._args), stdin=subprocess.DEVNULL, stdout=2
        )
        try:
            self._wait_until_ready()
        except BaseException:
            # Also on Ctrl+C: a server that loads models must not stay behind
            self.stop()
            raise
        return self

    def stop(self) -> None:
        """Stop the server. If the server does not run, do nothing."""
        process, self._process = self._process, None
        if process is None or process.poll() is not None:
            return
        process.terminate()
        try:
            process.wait(timeout=_STOP_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()

    def __enter__(self) -> "HybridServer":
        return self.start()

    def __exit__(self, *exc_info) -> None:
        self.stop()

    def _wait_until_ready(self) -> None:
        deadline = time.monotonic() + self._startup_timeout
        while not self._answers():
            try:
                code = self._process.wait(timeout=_POLL_SECONDS)
            except subprocess.TimeoutExpired:
                if time.monotonic() >= deadline:
                    raise TimeoutError(
                        f"The hybrid server did not answer at {self.url} "
                        f"within {self._startup_timeout} seconds"
                    ) from None
                continue
            raise RuntimeError(
                f"The hybrid server exited with code {code} during startup. "
                "See its log on stderr."
            )

    def _answers(self) -> bool:
        try:
            with _LOCAL_OPENER.open(
                self.url + "/health", timeout=_HEALTH_TIMEOUT_SECONDS
            ) as response:
                return response.status == 200
        except OSError:
            return False
