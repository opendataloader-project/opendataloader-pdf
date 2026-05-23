"""
Low-level JAR runner for opendataloader-pdf.
"""
import subprocess
import sys
import importlib.resources as resources
from typing import List, Optional

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


def run_jar(
    args: List[str],
    quiet: bool = False,
    timeout: Optional[float] = None,
) -> str:
    """Run the opendataloader-pdf JAR with the given arguments.

    Args:
        args: Arguments forwarded to the bundled JAR.
        quiet: Suppress live console streaming and capture stdout instead.
        timeout: Optional wall-clock timeout in seconds. If the JVM hasn't
            finished by then it is sent SIGKILL and ``subprocess.TimeoutExpired``
            is raised. ``None`` (default) preserves the original
            no-timeout behaviour. Useful when invoking the JAR from a
            long-running service where a single malformed/pathological PDF
            must not be able to stall the caller indefinitely.

    Returns:
        Captured stdout from the JAR. In streaming mode this is the same
        text that was tee-d to ``sys.stdout`` during the run.

    Raises:
        FileNotFoundError: ``java`` is not on PATH.
        subprocess.CalledProcessError: JAR exited non-zero.
        subprocess.TimeoutExpired: ``timeout`` was set and elapsed. The
            JVM has been killed by the time this is raised; partial
            output is in ``exc.stdout``.
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
                # Quiet mode → capture all output. ``subprocess.run`` natively
                # supports ``timeout``; on expiry it sends SIGKILL to the
                # child and raises ``TimeoutExpired`` — no orphan JVM.
                result = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    check=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=timeout,
                )
                return result.stdout

            # Streaming mode → live output. We can't rely on subprocess.run's
            # timeout here because we're tee-ing lines as they arrive. Track
            # the deadline manually and SIGKILL on expiry, then raise the
            # same ``TimeoutExpired`` shape so callers can ``except`` uniformly
            # across quiet/streaming modes.
            import time as _time
            deadline = (_time.monotonic() + timeout) if timeout is not None else None

            with subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
            ) as process:
                output_lines: List[str] = []
                try:
                    for line in process.stdout:
                        if hasattr(sys.stdout, "buffer"):
                            sys.stdout.buffer.write(
                                line.encode("utf-8", errors="replace")
                            )
                            sys.stdout.buffer.flush()
                        else:
                            sys.stdout.write(line)
                        output_lines.append(line)
                        if deadline is not None and _time.monotonic() > deadline:
                            process.kill()
                            captured = "".join(output_lines)
                            raise subprocess.TimeoutExpired(
                                command, timeout, output=captured
                            )

                    # ``timeout`` here covers the case where the process
                    # has finished streaming but is still in the wait()
                    # finalization (rare but possible).
                    if deadline is not None:
                        remaining = max(0.0, deadline - _time.monotonic())
                    else:
                        remaining = None
                    return_code = process.wait(timeout=remaining)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                    raise

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
        print(
            f"Error: opendataloader-pdf CLI exceeded timeout of {error.timeout}s.",
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
