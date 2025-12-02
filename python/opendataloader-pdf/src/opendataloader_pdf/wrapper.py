import argparse
import subprocess
import sys
import importlib.resources as resources
import locale
from pathlib import Path
from typing import List, Optional, Union

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
    use_struct_tree: bool = False,
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
        use_struct_tree: If True, enable processing structure tree (disabled by default)

    Raises:
        FileNotFoundError: If the 'java' command is not found or input_path is invalid.
        subprocess.CalledProcessError: If the CLI tool returns a non-zero exit code.
    """
    if not Path(input_path).exists():
        raise FileNotFoundError(f"Input file or folder not found: {input_path}")

    args = []
    args.append(input_path)
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
    if use_struct_tree:
        args.append("--use-struct-tree")

    # Run the command
    run_jar(args, quiet=not debug)


def convert(
    input_path: Union[str, List[str]],
    output_dir: Optional[str] = None,
    password: Optional[str] = None,
    format: Optional[Union[str, List[str]]] = None,
    quiet: bool = False,
    content_safety_off: Optional[Union[str, List[str]]] = None,
    keep_line_breaks: bool = False,
    replace_invalid_chars: Optional[str] = None,
    use_struct_tree: bool = False,
) -> None:
    """
    Convert PDF(s) into the requested output format(s).

    Args:
        input_path: One or more input PDF file paths or directories
        output_dir: Directory where outputs are written
        password: Password for encrypted PDFs
        format: Comma-separated output formats to generate (json, text, html, pdf, markdown, markdown-with-html, markdown-with-images)
        quiet: Suppress CLI logging output
        content_safety_off: Disable one or more content safety filters (all, hidden-text, off-page, tiny, hidden-ocg)
        keep_line_breaks: Preserve line breaks in text output
        replace_invalid_chars: Replacement character for invalid/unrecognized characters
        use_struct_tree: Enable processing structure tree (disabled by default)
    """
    args: List[str] = []

    if isinstance(input_path, list):
        args.extend(input_path)
    else:
        args.append(input_path)

    if output_dir:
        args.extend(["--output-dir", output_dir])
    if password:
        args.extend(["--password", password])
    if format:
        if isinstance(format, list):
            args.extend(["--format", ",".join(format)])
        else:
            args.extend(["--format", format])
    if quiet:
        args.extend(["--quiet"])
    if content_safety_off:
        if isinstance(content_safety_off, list):
            args.extend(["--content-safety-off", ",".join(content_safety_off)])
        else:
            args.extend(["--content-safety-off", content_safety_off])
    if keep_line_breaks:
        args.extend(["--keep-line-breaks"])
    if replace_invalid_chars:
        args.extend(["--replace-invalid-chars", replace_invalid_chars])
    if use_struct_tree:
        args.extend(["--use-struct-tree"])

    # Run the command
    run_jar(args, quiet)


def run_jar(args: List[str], quiet: bool = False) -> str:
    """Run the opendataloader-pdf JAR with the given arguments."""
    try:
        # Access the embedded JAR inside the package
        jar_ref = resources.files("opendataloader_pdf").joinpath("jar", _JAR_NAME)
        with resources.as_file(jar_ref) as jar_path:
            command = ["java", "-jar", str(jar_path), *args]

            if quiet:
                # Quiet mode → capture all output
                result = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    check=True,
                    encoding=locale.getpreferredencoding(False),
                )
                return result.stdout

            # Streaming mode → live output
            with subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding=locale.getpreferredencoding(False),
            ) as process:
                output_lines: List[str] = []
                for line in process.stdout:
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
        if error.output:
            print(f"Output: {error.output}", file=sys.stderr)
        if error.stderr:
            print(f"Stderr: {error.stderr}", file=sys.stderr)
        if error.stdout:
            print(f"Stdout: {error.stdout}", file=sys.stderr)
        raise


def main(argv=None) -> int:
    """CLI entry point for running the wrapper from the command line."""
    parser = argparse.ArgumentParser(
        description="Run the opendataloader-pdf CLI using the bundled JAR."
    )
    parser.add_argument(
        "input_path", nargs="+", help="Path to the input PDF file or directory."
    )
    parser.add_argument(
        "-o",
        "--output-dir",
        help="Directory where outputs are written.",
    )
    parser.add_argument("-p", "--password", help="Password for encrypted PDFs.")
    parser.add_argument(
        "-f",
        "--format",
        help="Comma-separated output formats to generate. (json, text, html, pdf, markdown, markdown-with-html, markdown-with-images)",
    )
    parser.add_argument(
        "-q",
        "--quiet",
        action="store_true",
        help="Suppress CLI logging output.",
    )
    parser.add_argument(
        "--content-safety-off",
        help="Comma-separated content safety filters to disable. (all, hidden-text, off-page, tiny, hidden-ocg)",
    )
    parser.add_argument(
        "--keep-line-breaks",
        action="store_true",
        help="Preserve line breaks in text output.",
    )
    parser.add_argument(
        "--replace-invalid-chars",
        help="Replacement character for invalid or unrecognized characters.",
    )
    parser.add_argument(
        "--use-struct-tree",
        action="store_true",
        help="Enable processing structure tree (disabled by default)",
    )
    args = parser.parse_args(argv)

    try:
        convert(**vars(args))
    except FileNotFoundError as err:
        print(err, file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as err:
        return err.returncode or 1


if __name__ == "__main__":
    sys.exit(main())
