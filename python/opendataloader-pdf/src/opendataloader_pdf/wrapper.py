import argparse
import subprocess
import sys
import importlib.resources as resources
import locale
import warnings
from typing import List, Optional, Union

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


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
    table_method: Optional[Union[str, List[str]]] = None,
    reading_order: Optional[str] = None,
    markdown_page_separator: Optional[str] = None,
    text_page_separator: Optional[str] = None,
    html_page_separator: Optional[str] = None,
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
        table_method: Specified table detection method.
        markdown_page_separator: Specifies the separator string inserted between pages in the markdown output.
        text_page_separator: Specifies the separator string inserted between pages in the text output.
        html_page_separator: Specifies the separator string inserted between pages in the html output.
    """
    args: List[str] = []

    if isinstance(input_path, list):
        args.extend(input_path)
    else:
        args.append(input_path)

    if output_dir:
        args.append("--output-dir")
        args.append(output_dir)
    if password:
        args.append("--password")
        args.append(password)
    if format:
        if isinstance(format, list):
            args.append("--format")
            args.append(",".join(format))
        else:
            args.append("--format")
            args.append(format)
    if quiet:
        args.append("--quiet")
    if content_safety_off:
        if isinstance(content_safety_off, list):
            args.append("--content-safety-off")
            args.append(",".join(content_safety_off))
        else:
            args.append("--content-safety-off")
            args.append(content_safety_off)
    if keep_line_breaks:
        args.append("--keep-line-breaks")
    if replace_invalid_chars:
        args.append("--replace-invalid-chars")
        args.append(replace_invalid_chars)
    if use_struct_tree:
        args.append("--use-struct-tree")
    if table_method:
        if isinstance(table_method, list):
            args.append("--table-method")
            args.append(",".join(table_method))
        else:
            args.append("--table-method")
            args.append(table_method)
    if reading_order:
        args.append("--reading-order")
        args.append(reading_order)
    if markdown_page_separator:
        args.append("--markdown-page-separator")
        args.append(markdown_page_separator)
    if text_page_separator:
        args.append("--text-page-separator")
        args.append(text_page_separator)
    if html_page_separator:
        args.append("--html-page-separator")
        args.append(html_page_separator)

    # Run the command
    run_jar(args, quiet)


# Deprecated : Use `convert()` instead. This function will be removed in a future version.
def run(
    input_path: str,
    output_folder: Optional[str] = None,
    password: Optional[str] = None,
    replace_invalid_chars: Optional[str] = None,
    generate_markdown: bool = False,
    generate_html: bool = False,
    generate_annotated_pdf: bool = False,
    keep_line_breaks: bool = False,
    content_safety_off: Optional[str] = None,
    html_in_markdown: bool = False,
    add_image_to_markdown: bool = False,
    no_json: bool = False,
    debug: bool = False,
    use_struct_tree: bool = False,
):
    """
    Runs the opendataloader-pdf with the given arguments.

    .. deprecated::
        Use :func:`convert` instead. This function will be removed in a future version.

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
    warnings.warn(
        "run() is deprecated and will be removed in a future version. Use convert() instead.",
        DeprecationWarning,
        stacklevel=2,
    )

    # Build format list based on legacy boolean options
    formats: List[str] = []
    if not no_json:
        formats.append("json")
    if generate_markdown:
        if add_image_to_markdown:
            formats.append("markdown-with-images")
        elif html_in_markdown:
            formats.append("markdown-with-html")
        else:
            formats.append("markdown")
    if generate_html:
        formats.append("html")
    if generate_annotated_pdf:
        formats.append("pdf")

    convert(
        input_path=input_path,
        output_dir=output_folder,
        password=password,
        replace_invalid_chars=replace_invalid_chars,
        keep_line_breaks=keep_line_breaks,
        content_safety_off=content_safety_off,
        use_struct_tree=use_struct_tree,
        format=formats if formats else None,
        quiet=not debug,
    )


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
    parser.add_argument(
        "--table-method",
        help="Enable specified table detection method. Accepts a comma-separated list of methods.",
    )
    parser.add_argument(
        "--reading-order",
        help="Specifies reading order of content. Supported values: bbox",
    )
    parser.add_argument(
        "--markdown-page-separator",
        help='Specifies the separator string inserted between pages in the markdown output. Use "%page-number%" inside the string to include the current page number.',
    )
    parser.add_argument(
        "--text-page-separator",
        help='Specifies the separator string inserted between pages in the text output. Use "%page-number%" inside the string to include the current page number.',
    )
    parser.add_argument(
        "--html-page-separator",
        help='Specifies the separator string inserted between pages in the html output. Use "%page-number%" inside the string to include the current page number.',
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
