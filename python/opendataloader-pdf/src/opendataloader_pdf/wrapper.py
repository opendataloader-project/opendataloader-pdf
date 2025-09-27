import argparse
import subprocess
import sys
import importlib.resources as importlib_resources
import locale
from pathlib import Path

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


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
    no_json: bool = False,
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
        no_json: If True, disable the JSON output.
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
    if content_safety_off:
        args.extend(["--content-safety-off", content_safety_off])
    if generate_markdown:
        args.append("--markdown")
    if generate_html:
        args.append("--html")
    if generate_annotated_pdf:
        args.append("--pdf")
    if keep_line_breaks:
        args.append("--keep-line-breaks")
    if html_in_markdown:
        args.append("--markdown-with-html")
    if add_image_to_markdown:
        args.append("--markdown-with-images")
    if no_json:
        args.append("--no-json")

    args.append(input_path)

    try:
        # Find the JAR file within the package
        jar_ref = importlib_resources.files("opendataloader_pdf").joinpath(
            "jar", _JAR_NAME
        )
        with importlib_resources.as_file(jar_ref) as jar_path:
            command = ["java", "-jar", str(jar_path)] + args

            if debug:
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


def main(argv=None) -> int:
    """CLI entry point for running the wrapper from the command line."""
    parser = argparse.ArgumentParser(
        description="Run the opendataloader-pdf CLI using the bundled JAR."
    )
    parser.add_argument("input_path", help="Path to the input PDF file or directory.")
    parser.add_argument(
        "-o",
        "--output-dir",
        dest="output_folder",
        help="Directory where outputs are written.",
    )
    parser.add_argument("-p", "--password", help="Password for encrypted PDFs.")
    parser.add_argument(
        "--replace-invalid-chars",
        help="Replacement character for invalid or unrecognized characters.",
    )
    parser.add_argument(
        "--content-safety-off",
        help="Disable content safety filtering (expects the desired mode).",
    )
    parser.add_argument(
        "--markdown",
        dest="generate_markdown",
        action="store_true",
        help="Generate Markdown output.",
    )
    parser.add_argument(
        "--html",
        dest="generate_html",
        action="store_true",
        help="Generate HTML output.",
    )
    parser.add_argument(
        "--pdf",
        dest="generate_annotated_pdf",
        action="store_true",
        help="Generate annotated PDF output.",
    )
    parser.add_argument(
        "--keep-line-breaks",
        action="store_true",
        help="Preserve line breaks in text output.",
    )
    parser.add_argument(
        "--markdown-with-html",
        dest="html_in_markdown",
        action="store_true",
        help="Allow raw HTML within Markdown output.",
    )
    parser.add_argument(
        "--markdown-with-images",
        dest="add_image_to_markdown",
        action="store_true",
        help="Embed images in Markdown output.",
    )
    parser.add_argument(
        "--no-json",
        action="store_true",
        help="Disable JSON output generation.",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Stream CLI logs directly to stdout.",
    )
    args = parser.parse_args(argv)

    try:
        run(**vars(args))
    except FileNotFoundError as err:
        print(err, file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as err:
        return err.returncode or 1


if __name__ == "__main__":
    sys.exit(main())
