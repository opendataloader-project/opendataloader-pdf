"""
Low-level JAR runner for opendataloader-pdf.
"""
import json
import math
import os
import subprocess
import sys
import tempfile
import threading
import importlib.resources as resources
from typing import IO, Dict, List, Optional, Tuple

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"

# Environment variables that the JVM reads options from
_JAVA_OPTION_VARS = ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")


def _temp_dir_options() -> List[str]:
    """Return the option that gives Java the temporary folder of this runtime.

    The JVM does not read TMPDIR, so without this option Java can use another
    folder than Python. A command-line option wins over JAVA_TOOL_OPTIONS and
    JDK_JAVA_OPTIONS, so add none if the user set java.io.tmpdir there.
    """
    for name in _JAVA_OPTION_VARS:
        if "-Djava.io.tmpdir=" in os.environ.get(name, ""):
            return []
    return [f"-Djava.io.tmpdir={_ascii_path(tempfile.gettempdir())}"]


_ON_WINDOWS = os.name == "nt"

# Environment variable that the CLI reads its arguments from, as a JSON array,
# when the command line has none
_ARGS_ENV_VAR = "OPENDATALOADER_PDF_ARGS"


def _short_path(path: str) -> Optional[str]:
    """Return the Windows short (8.3) form of an existing path, or None.

    Returns None on other platforms or if the path does not exist. If the
    path has no short name, for example on a volume that does not create
    8.3 names, GetShortPathNameW returns the long path, so this function
    returns that path, not None.
    """
    if not _ON_WINDOWS:
        return None
    # ctypes.windll exists only on Windows, so import here
    import ctypes
    from ctypes import wintypes

    get_short_path = ctypes.windll.kernel32.GetShortPathNameW
    get_short_path.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.DWORD]
    get_short_path.restype = wintypes.DWORD

    length = get_short_path(path, None, 0)
    if length == 0:
        return None
    buffer = ctypes.create_unicode_buffer(length)
    if get_short_path(path, buffer, length) == 0:
        return None
    return buffer.value


def _ascii_path(path: str) -> str:
    """Return a form of an existing path that the Java launcher can read.

    On Windows the Java launcher decodes the command line with the ANSI code
    page of the system, not UTF-8. Characters outside that code page (Greek,
    Cyrillic, CJK on a Western-locale system) become "?". The short (8.3) form
    of a path is ASCII and keeps its meaning. Used for the JAR path and the
    temporary folder, which are JVM options and never name output.
    Return the path unchanged if it is ASCII or has no ASCII short form.
    """
    if path.isascii():
        return path
    short = _short_path(os.path.abspath(path))
    return short if short and short.isascii() else path


def _jar_arguments(args: List[str]) -> Tuple[List[str], Optional[Dict[str, str]]]:
    """Return the command-line arguments and the environment for the JAR.

    On Windows the Java launcher turns characters outside the ANSI code page
    into "?", so Java cannot find a non-ASCII input path, and output names and
    image links would be wrong. The CLI also reads its arguments from
    OPENDATALOADER_PDF_ARGS, so pass them there instead. json.dumps escapes
    non-ASCII characters, so the value is ASCII and survives any code page.
    The environment is None when the arguments go on the command line.
    """
    # subprocess accepts pathlib.Path arguments, so accept them here too
    args = [os.fspath(arg) for arg in args]
    if not _ON_WINDOWS or all(arg.isascii() for arg in args):
        return args, None
    return [], {**os.environ, _ARGS_ENV_VAR: json.dumps(args)}


# How long to wait for the relay thread after the JVM has been KILLED. Only the
# timeout path uses this: the output is incomplete by definition once the JVM is
# killed mid-document, so there is nothing to be gained by waiting on it, and a
# bound keeps a wedged read from holding the caller. The success path joins
# without a bound instead -- see `run_jar`. The thread is a daemon, so
# overrunning this never blocks interpreter exit.
_RELAY_JOIN_TIMEOUT_S = 1.0


def _write_stdout(text: str) -> None:
    """Relay JAR output to the parent stdout, preserving UTF-8 bytes."""
    if hasattr(sys.stdout, "buffer"):
        sys.stdout.buffer.write(text.encode("utf-8", errors="replace"))
        sys.stdout.buffer.flush()
    else:
        sys.stdout.write(text)


def _relay_lines(stream: IO[str], sink: List[str], errors: List[Exception]) -> None:
    """Write every line of ``stream`` to stdout, collecting it in ``sink``.

    Runs on a helper thread, where an uncaught exception would only end the
    thread, so a failure is recorded in ``errors`` for ``run_jar`` to re-raise.
    The stream is then closed: nothing else reads it, and a child still
    writing would otherwise block on the full pipe until the timeout.
    """
    try:
        for line in stream:
            # Collect first: the kill path reads `sink` after a bounded join,
            # possibly while this write is still blocked.
            sink.append(line)
            _write_stdout(line)
    except Exception as error:  # re-raised on the calling thread by run_jar
        errors.append(error)
        stream.close()


def _report_failure(error: BaseException, message: str, relay: bool) -> None:
    """Write a failure message to stderr, or, without relay, add it to the error.

    Without relay (the library API), nothing goes to the console, also on a
    failure. The message goes into the notes of the error, which a traceback
    shows.
    """
    if relay:
        print(message, file=sys.stderr)
    else:
        error.add_note(message)


def run_jar(
    args: List[str],
    quiet: bool = False,
    timeout: Optional[float] = None,
    *,
    relay: bool = True,
) -> str:
    """Run the opendataloader-pdf JAR with the given arguments.

    Args:
        args: Arguments to pass to the CLI.
        quiet: Suppress the JAR's log stream (stderr) and return its stdout.
        relay: Write the JAR's output to the console as well as returning it
            (the default). The bundled CLI uses this. ``False`` writes nothing
            to the console, whatever ``quiet`` is: the JAR's stdout is
            returned and its log stream (stderr) is dropped. On a failure, the
            message that ``True`` writes to stderr goes into the notes of the
            raised error instead. ``convert()`` uses ``False``.
        timeout: Wall-clock limit in seconds for the JAR process. ``None``
            (the default) waits indefinitely, which is the historical
            behaviour. On expiry the JVM is killed and
            ``subprocess.TimeoutExpired`` is raised. The CLI declares no
            processing bound of its own -- ``--hybrid-timeout`` covers the
            hybrid HTTP call only -- so this is the only way for a caller to
            stop waiting on a conversion that does not return. It must be
            positive: ``0`` is **not** "no timeout" here, unlike
            ``--hybrid-timeout``, so it is rejected rather than read as an
            immediate one. Pass ``None``, or ``float("inf")``, to wait
            indefinitely. The bound applies to the JVM process itself: a child
            process that the JVM starts is not killed with it, and in streaming
            mode one that keeps the output pipe open can keep the call from
            returning.

    Raises:
        ValueError: If ``timeout`` is not ``None`` and not greater than zero.
        FileNotFoundError: If the 'java' command is not found.
        subprocess.CalledProcessError: If the CLI returns a non-zero exit code.
        subprocess.TimeoutExpired: If ``timeout`` elapses before the CLI exits.
    """
    # `not timeout > 0` also rejects NaN, which `timeout <= 0` would let through.
    if timeout is not None and not timeout > 0:
        raise ValueError(f"timeout must be a positive number of seconds or None, got {timeout!r}")
    # An infinite bound is no bound, but subprocess cannot take it: it converts
    # the bound to an integer and raises OverflowError after the JVM has started.
    # Wait without a bound instead, on the path of a bounded run, which kills
    # the JVM on an interrupt.
    wait_timeout = None if timeout == math.inf else timeout

    try:
        # Access the embedded JAR inside the package
        jar_ref = resources.files("opendataloader_pdf").joinpath("jar", _JAR_NAME)
        with resources.as_file(jar_ref) as jar_path:
            jar_args, env = _jar_arguments(args)
            # Force headless AWT so macOS doesn't surface a Dock icon (and
            # steal focus) every time the JVM touches ImageIO/PDFBox
            # rendering. Safe on all OSes — the CLI never opens a UI window,
            # only manipulates BufferedImages.
            command = [
                "java",
                "-Djava.awt.headless=true",
                "-Dapple.awt.UIElement=true",
                *_temp_dir_options(),
                "-jar",
                _ascii_path(str(jar_path)),
                *jar_args,
            ]

            if quiet or not relay:
                # Quiet mode → suppress the JAR's log stream (stderr) but
                # relay its stdout to the caller: --to-stdout content and the
                # folder summary line arrive on stdout, and swallowing them
                # breaks pipe consumers (`... --quiet --to-stdout | jq`).
                # Without relay (the library API) → only return the stdout.
                result = subprocess.run(
                    command,
                    env=env,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=True,
                    encoding="utf-8",
                    errors="replace",
                    # subprocess.run kills the child and reaps it before
                    # raising TimeoutExpired, so no JVM is left behind.
                    timeout=wait_timeout,
                )
                if relay and result.stdout:
                    _write_stdout(result.stdout)
                return result.stdout

            # Streaming mode → live output
            with subprocess.Popen(
                command,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
            ) as process:
                output_lines: List[str] = []

                if timeout is None:
                    for line in process.stdout:
                        _write_stdout(line)
                        output_lines.append(line)
                    return_code = process.wait()
                else:
                    # A blocking read on the pipe cannot itself be bounded, so
                    # the relay runs on a helper thread and the timeout is
                    # applied to the process: a JVM that wedges without
                    # emitting another line is still stopped.
                    relay_errors: List[Exception] = []
                    reader = threading.Thread(
                        target=_relay_lines,
                        args=(process.stdout, output_lines, relay_errors),
                        daemon=True,
                    )
                    reader.start()
                    try:
                        return_code = process.wait(timeout=wait_timeout)
                    except BaseException as stopped:
                        # Kill before propagating, for the timeout and for an
                        # interrupt alike: the caller stops waiting, but an
                        # unkilled JVM keeps the CPU and its output directory.
                        # An interrupt must not leave the JVM running either:
                        # leaving the `with` block would close the pipe while
                        # the relay thread still reads it, and wait for the JVM.
                        process.kill()
                        process.wait()
                        reader.join(timeout=_RELAY_JOIN_TIMEOUT_S)
                        if isinstance(stopped, subprocess.TimeoutExpired):
                            # Attach what the JAR had already emitted to the
                            # original exception rather than raising a second
                            # one, so the traceback still points at the wait()
                            # that timed out. Bytes, or None if nothing arrived,
                            # as subprocess.run documents for
                            # TimeoutExpired.output in quiet mode.
                            stopped.output = "".join(output_lines).encode("utf-8") or None
                            if relay_errors:
                                # The relay failed first; that is the cause the
                                # caller needs, with the timeout attached.
                                raise relay_errors[0] from stopped
                        raise
                    # Success: the JVM has exited, so the pipe reaches EOF and
                    # the relay ends on its own. Joining WITHOUT a bound here is
                    # deliberate -- the JVM can exit while the pipe still holds
                    # buffered output, and relaying that onward can outlast any
                    # short bound when the parent's stdout is slow. Cutting the
                    # join short would silently truncate `captured_output`,
                    # including a `--to-stdout` payload. `timeout` bounds the
                    # JVM, not the caller's own stdout.
                    reader.join()
                    if relay_errors:
                        raise relay_errors[0]

                captured_output = "".join(output_lines)

                if return_code:
                    raise subprocess.CalledProcessError(
                        return_code, command, output=captured_output
                    )
                return captured_output

    except FileNotFoundError as error:
        _report_failure(
            error,
            "Error: 'java' command not found. Please ensure Java is installed and in your system's PATH.",
            relay,
        )
        raise

    except subprocess.TimeoutExpired as error:
        # The JVM has already been killed; surface the bound that was hit so a
        # timeout is distinguishable from a crash in the caller's logs.
        _report_failure(error, f"opendataloader-pdf CLI timed out after {error.timeout}s.", relay)
        raise

    except subprocess.CalledProcessError as error:
        lines = ["Error running opendataloader-pdf CLI.", f"Return code: {error.returncode}"]
        # Streaming mode already wrote the JAR's output live to stdout, so
        # re-printing the captured copy would duplicate it. Only surface the
        # captured streams in quiet mode or without relay, where the caller
        # has not seen them.
        # Note: CalledProcessError.output and .stdout are aliases for the same
        # attribute — printing both produces the same content twice.
        if quiet or not relay:
            if error.stdout:
                lines.append(f"Stdout: {error.stdout}")
            if error.stderr:
                lines.append(f"Stderr: {error.stderr}")
        _report_failure(error, "\n".join(lines), relay)
        raise
