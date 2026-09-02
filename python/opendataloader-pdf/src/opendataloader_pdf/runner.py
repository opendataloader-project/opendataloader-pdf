"""
Low-level JAR runner for opendataloader-pdf.
"""
import subprocess
import sys
import threading
import importlib.resources as resources
from typing import IO, List, Optional

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"

# How long to wait for the relay thread after the JVM has exited or been
# killed. The thread only drains a pipe that is already closed, so it ends
# immediately in practice; the bound exists so a wedged read cannot hold the
# caller. It is a daemon thread, so overrunning it never blocks interpreter
# exit.
_RELAY_JOIN_TIMEOUT_S = 1.0


def _write_stdout(text: str) -> None:
    """Relay JAR output to the parent stdout, preserving UTF-8 bytes."""
    if hasattr(sys.stdout, "buffer"):
        sys.stdout.buffer.write(text.encode("utf-8", errors="replace"))
        sys.stdout.buffer.flush()
    else:
        sys.stdout.write(text)


def _relay_lines(stream: IO[str], sink: List[str]) -> None:
    """Write every line of ``stream`` to stdout, collecting it in ``sink``."""
    for line in stream:
        _write_stdout(line)
        sink.append(line)


def run_jar(args: List[str], quiet: bool = False, timeout: Optional[float] = None) -> str:
    """Run the opendataloader-pdf JAR with the given arguments.

    Args:
        args: Arguments to pass to the CLI.
        quiet: Suppress the JAR's log stream (stderr) and return its stdout.
        timeout: Wall-clock limit in seconds for the JAR process. ``None``
            (the default) waits indefinitely, which is the historical
            behaviour. On expiry the JVM is killed and
            ``subprocess.TimeoutExpired`` is raised. The CLI declares no
            processing bound of its own -- ``--hybrid-timeout`` covers the
            hybrid HTTP call only -- so this is the only way for a caller to
            stop waiting on a conversion that does not return. Note that ``0``
            is **not** "no timeout" here, unlike ``--hybrid-timeout``: it means
            an immediate one. Pass ``None`` to wait indefinitely.

    Raises:
        FileNotFoundError: If the 'java' command is not found.
        subprocess.CalledProcessError: If the CLI returns a non-zero exit code.
        subprocess.TimeoutExpired: If ``timeout`` elapses before the CLI exits.
    """
    try:
        # Access the embedded JAR inside the package
        jar_ref = resources.files("opendataloader_pdf").joinpath("jar", _JAR_NAME)
        with resources.as_file(jar_ref) as jar_path:
            # Force headless AWT so macOS doesn't surface a Dock icon (and
            # steal focus) every time the JVM touches ImageIO/PDFBox
            # rendering. Safe on all OSes — the CLI never opens a UI window,
            # only manipulates BufferedImages.
            command = [
                "java",
                "-Djava.awt.headless=true",
                "-Dapple.awt.UIElement=true",
                "-jar",
                str(jar_path),
                *args,
            ]

            if quiet:
                # Quiet mode → suppress the JAR's log stream (stderr) but
                # relay its stdout to the caller: --to-stdout content and the
                # folder summary line arrive on stdout, and swallowing them
                # breaks pipe consumers (`... --quiet --to-stdout | jq`).
                result = subprocess.run(
                    command,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=True,
                    encoding="utf-8",
                    errors="replace",
                    # subprocess.run kills the child and reaps it before
                    # raising TimeoutExpired, so no JVM is left behind.
                    timeout=timeout,
                )
                if result.stdout:
                    _write_stdout(result.stdout)
                return result.stdout

            # Streaming mode → live output
            with subprocess.Popen(
                command,
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
                    reader = threading.Thread(
                        target=_relay_lines,
                        args=(process.stdout, output_lines),
                        daemon=True,
                    )
                    reader.start()
                    try:
                        return_code = process.wait(timeout=timeout)
                    except subprocess.TimeoutExpired as expired:
                        # Kill before propagating: the caller stops waiting, but
                        # an unkilled JVM keeps the CPU and its output directory.
                        process.kill()
                        process.wait()
                        reader.join(timeout=_RELAY_JOIN_TIMEOUT_S)
                        # Attach what the JAR had already emitted to the original
                        # exception rather than raising a second one, so the
                        # traceback still points at the wait() that timed out.
                        expired.output = "".join(output_lines)
                        raise
                    reader.join(timeout=_RELAY_JOIN_TIMEOUT_S)

                captured_output = "".join(output_lines)

                if return_code:
                    raise subprocess.CalledProcessError(
                        return_code, command, output=captured_output
                    )
                return captured_output

    except FileNotFoundError:
        print(
            "Error: 'java' command not found. Please ensure Java is installed and in your system's PATH.",
            file=sys.stderr,
        )
        raise

    except subprocess.TimeoutExpired as error:
        # The JVM has already been killed; surface the bound that was hit so a
        # timeout is distinguishable from a crash in the caller's logs.
        print(
            f"opendataloader-pdf CLI timed out after {error.timeout}s.",
            file=sys.stderr,
        )
        raise

    except subprocess.CalledProcessError as error:
        print("Error running opendataloader-pdf CLI.", file=sys.stderr)
        print(f"Return code: {error.returncode}", file=sys.stderr)
        # Streaming mode already wrote the JAR's output live to stdout, so
        # re-printing the captured copy would duplicate it. Only surface the
        # captured streams in quiet mode, where the caller has not seen them.
        # Note: CalledProcessError.output and .stdout are aliases for the same
        # attribute — printing both produces the same content twice.
        if quiet:
            if error.stdout:
                print(f"Stdout: {error.stdout}", file=sys.stderr)
            if error.stderr:
                print(f"Stderr: {error.stderr}", file=sys.stderr)
        raise
