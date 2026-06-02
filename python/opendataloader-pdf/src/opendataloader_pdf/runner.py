"""
Low-level JAR runner for opendataloader-pdf.
"""
import subprocess
import sys
import importlib.resources as resources
from typing import List

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


# >>> opendataloader-pdf non-ASCII path shim (cross-platform; active on Windows)
def _make_jvm_safe_args(args):
    """Return *args* with non-ASCII filesystem paths made safe for the JVM.

    On Windows the Java launcher decodes command-line arguments with the system
    ANSI code page (``sun.jnu.encoding``) rather than UTF-8. Any path containing
    characters outside that code page -- Greek, Cyrillic, CJK and so on, on a
    Western-locale machine -- is replaced by ``?`` before the JAR can see it, so
    the input file is reported "not found" and output cannot be written.

    To stay encoding-safe we swap such arguments for their Windows short (8.3)
    path, which is pure ASCII and therefore survives the code-page round-trip.
    Output directories passed via ``--output-dir``/``-o``/``--image-dir`` are
    created first so a short name exists. The function is a no-op on non-Windows
    platforms (UTF-8 default charset) and for arguments that are already ASCII
    or are not filesystem paths.
    """
    import os

    if os.name != "nt":
        return args

    import ctypes
    from ctypes import wintypes

    _get_short = ctypes.windll.kernel32.GetShortPathNameW
    _get_short.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.DWORD]
    _get_short.restype = wintypes.DWORD

    def _short_path(path):
        # GetShortPathName requires the target to exist; returns 0 on failure.
        length = _get_short(path, None, 0)
        if length == 0:
            return None
        buffer = ctypes.create_unicode_buffer(length)
        if _get_short(path, buffer, length) == 0:
            return None
        return buffer.value

    def _fix_input(arg):
        if not isinstance(arg, str) or arg.isascii() or not os.path.exists(arg):
            return arg
        short = _short_path(os.path.abspath(arg))
        return short if short and short.isascii() else arg

    def _fix_output_dir(arg):
        if not isinstance(arg, str) or arg.isascii():
            return arg
        try:
            os.makedirs(os.path.abspath(arg), exist_ok=True)
        except OSError:
            return arg
        short = _short_path(os.path.abspath(arg))
        return short if short and short.isascii() else arg

    path_flags = {"--output-dir", "-o", "--image-dir"}
    safe = []
    expect_dir = False
    for arg in args:
        if expect_dir:
            expect_dir = False
            safe.append(_fix_output_dir(arg))
            continue
        if arg in path_flags:
            expect_dir = True
            safe.append(arg)
            continue
        if isinstance(arg, str) and "=" in arg and arg.split("=", 1)[0] in path_flags:
            flag, value = arg.split("=", 1)
            safe.append(flag + "=" + _fix_output_dir(value))
            continue
        safe.append(_fix_input(arg))
    return safe
# <<< opendataloader-pdf non-ASCII path shim


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
            args = _make_jvm_safe_args(args)
            command = [
                "java",
                "-Djava.awt.headless=true",
                "-Dapple.awt.UIElement=true",
                "-jar",
                str(jar_path),
                *args,
            ]

            if quiet:
                # Quiet mode → capture all output
                result = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    check=True,
                    encoding="utf-8",
                    errors="replace",
                )
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
