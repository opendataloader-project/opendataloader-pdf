"""
Low-level JAR runner for opendataloader-pdf.
"""
import os
import subprocess
import sys
import tempfile
import importlib.resources as resources
from typing import List, Optional

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


def _short_path(path: str) -> Optional[str]:
    """Return the Windows short (8.3) form of an existing path, or None.

    Returns None on other platforms, if the path does not exist, or if the
    volume does not create short names.
    """
    if os.name != "nt":
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
    Cyrillic, CJK on a Western-locale system) become "?", so Java cannot find
    the file. The short (8.3) form of a path is ASCII and keeps its meaning.
    Return the path unchanged if it is ASCII or has no ASCII short form.
    """
    if path.isascii():
        return path
    short = _short_path(os.path.abspath(path))
    return short if short and short.isascii() else path


# Options whose value is an output folder that can be missing before the run
_OUTPUT_DIR_OPTIONS = {"--output-dir", "-o", "--image-dir"}


def _ascii_output_dir(path: str) -> str:
    """Create an output folder, so that it has a short form, then shorten it."""
    if path.isascii():
        return path
    try:
        os.makedirs(path, exist_ok=True)
    except OSError:
        return path
    return _ascii_path(path)


def _ascii_input(arg: str) -> str:
    """Shorten an argument only if it names an existing file or folder."""
    if arg.isascii() or not os.path.exists(arg):
        return arg
    return _ascii_path(arg)


def _make_jvm_safe_args(args: List[str]) -> List[str]:
    """Return the CLI arguments with non-ASCII paths changed to short paths.

    Only existing paths and the values of output folder options change, so
    other values (passwords, separators) stay as they are.
    """
    safe_args: List[str] = []
    expect_output_dir = False
    for arg in args:
        # subprocess accepts pathlib.Path arguments, so accept them here too
        arg = os.fspath(arg)
        if expect_output_dir:
            safe_args.append(_ascii_output_dir(arg))
            expect_output_dir = False
        elif arg in _OUTPUT_DIR_OPTIONS:
            safe_args.append(arg)
            expect_output_dir = True
        elif arg.startswith("-") and "=" in arg:
            option, value = arg.split("=", 1)
            if option in _OUTPUT_DIR_OPTIONS:
                safe_args.append(f"{option}={_ascii_output_dir(value)}")
            else:
                safe_args.append(f"{option}={_ascii_input(value)}")
        else:
            safe_args.append(_ascii_input(arg))
    return safe_args


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
                _ascii_path(str(jar_path)),
                *_make_jvm_safe_args(args),
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
