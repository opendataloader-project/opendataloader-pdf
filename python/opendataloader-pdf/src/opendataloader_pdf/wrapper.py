import subprocess
import sys
import importlib_resources
import locale
from pathlib import Path
from typing import List

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


def _get_redacted_command_string(command: List[str]) -> str:
    """Redacts the password from a command list for safe logging."""
    command_for_logging = list(command)
    try:
        password_index = command_for_logging.index("--password")
        if password_index + 1 < len(command_for_logging):
            command_for_logging[password_index + 1] = "[REDACTED]"
    except ValueError:
        pass  # '--password' not in command
    return " ".join(command_for_logging)


def run(
    input_path: str,
    output_folder: str = None,
    password: str = None,
    replace_invalid_chars: str = None,
    generate_markdown: bool = False,
    generate_html: bool = False,
    generate_annotated_pdf: bool = False,
    keep_line_breaks: bool = False,
    content_safety_off: str = None,
    html_in_markdown: bool = False,
    add_image_to_markdown: bool = False,
    debug: bool = False,
):
    """
    Runs the opendataloader-pdf with the given arguments.

    Args:
        input_path: Path to the input PDF file or folder.
        output_folder: Path to the output folder. Defaults to the input folder.
        password: Password for the PDF file.
        replace_invalid_chars: Character to replace invalid or unrecognized characters (e.g., , \u0000) with.
        generate_markdown: If True, generates a Markdown output file.
        generate_html: If True, generates an HTML output file.
        generate_annotated_pdf: If True, generates an annotated PDF output file.
        keep_line_breaks: If True, keeps line breaks in the output.
        html_in_markdown: If True, uses HTML in the Markdown output.
        add_image_to_markdown: If True, adds images to the Markdown output.
        debug: If True, prints all messages from the CLI to the console during execution.

    Returns:
        The stdout from the CLI tool if successful.

    Raises:
        FileNotFoundError: If the 'java' command is not found or input_path is invalid.
        subprocess.CalledProcessError: If the CLI tool returns a non-zero exit code.
    """
    if not Path(input_path).exists():
        raise FileNotFoundError(f"Input file or folder not found: {input_path}")

    args = []
    if output_folder:
        args.extend(["--output-dir", output_folder])
    if password:
        args.extend(["--password", password])
    if replace_invalid_chars:
        args.extend(["--replace-invalid-chars", replace_invalid_chars])
    if generate_markdown:
        args.append("--markdown")
    if generate_html:
        args.append("--html")
    if generate_annotated_pdf:
        args.append("--pdf")
    if keep_line_breaks:
        args.append("--keep-line-breaks")
    if content_safety_off:
        args.append(["--content-safety-off", content_safety_off])
    if html_in_markdown:
        args.append("--markdown-with-html")
    if add_image_to_markdown:
        args.append("--markdown-with-images")

    args.append(input_path)

    try:
        # Find the JAR file within the package
        jar_ref = importlib_resources.files("opendataloader_pdf").joinpath(
            "jar", _JAR_NAME
        )
        with importlib_resources.as_file(jar_ref) as jar_path:
            command = ["java", "-jar", str(jar_path)] + args

            if debug:
                print(f"Running command: {_get_redacted_command_string(command)}", file=sys.stderr)
                process = subprocess.Popen(
                    command,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding=locale.getpreferredencoding(False),
                )

                output_lines = []
                for line in iter(process.stdout.readline, ""):
                    sys.stdout.write(line)
                    output_lines.append(line)

                process.stdout.close()
                return_code = process.wait()
                captured_output = "".join(output_lines)

                if return_code:
                    # Manually raise error with the combined output
                    raise subprocess.CalledProcessError(
                        return_code, command, output=captured_output
                    )
                return captured_output
            else:
                result = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    check=True,
                    encoding=locale.getpreferredencoding(False),
                )
                return result.stdout

    except FileNotFoundError:
        print(
            "Error: 'java' command not found. Please ensure Java is installed and in your system's PATH.",
            file=sys.stderr,
        )
        raise

    except subprocess.CalledProcessError as e:
        print("Error running opendataloader-pdf CLI.", file=sys.stderr)
        print(f"Return code: {e.returncode}", file=sys.stderr)
        if e.output:
            print(f"Output: {e.output}", file=sys.stderr)
        elif e.stderr:
            print(f"Stderr: {e.stderr}", file=sys.stderr)
        if e.stdout:
            print(f"Stdout: {e.stdout}", file=sys.stderr)
        raise e