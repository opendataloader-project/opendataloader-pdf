"""
Low-level JAR runner for opendataloader-pdf.
"""
import os
import subprocess
import sys
import tempfile
import importlib.resources as resources
from typing import List

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
    return [f"-Djava.io.tmpdir={tempfile.gettempdir()}"]


def run_jar(args: List[str], quiet: bool = False) -> str:
    """Run the opendataloader-pdf JAR with the given arguments."""
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
                *_temp_dir_options(),
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
                )
                if result.stdout:
                    if hasattr(sys.stdout, "buffer"):
                        sys.stdout.buffer.write(
                            result.stdout.encode("utf-8", errors="replace")
                        )
                        sys.stdout.buffer.flush()
                    else:
                        sys.stdout.write(result.stdout)
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
                for line in process.stdout:
                    if hasattr(sys.stdout, "buffer"):
                        sys.stdout.buffer.write(line.encode("utf-8", errors="replace"))
                        sys.stdout.buffer.flush()
                    else:
                        sys.stdout.write(line)
                    output_lines.append(line)

                return_code = process.wait()
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
