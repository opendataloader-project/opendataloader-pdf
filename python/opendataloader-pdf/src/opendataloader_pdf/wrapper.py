import argparse
import subprocess
import sys
import warnings
from typing import List, Optional

from .cli_options_generated import add_options_to_parser
from .convert_generated import convert
from .runner import run_jar

# Re-export for backward compatibility
__all__ = ["convert", "run", "run_jar", "main"]


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
        replace_invalid_chars: Character to replace invalid or unrecognized characters (e.g., , \\u0000) with.
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


def main(argv=None) -> int:
    """CLI entry point for running the wrapper from the command line."""
    parser = argparse.ArgumentParser(
        description="Run the opendataloader-pdf CLI using the bundled JAR."
    )
    parser.add_argument(
        "input_path", nargs="+", help="Path to the input PDF file or directory."
    )

    # Register CLI options from auto-generated module
    add_options_to_parser(parser)

    args = parser.parse_args(argv)

    try:
        convert(**vars(args))
        return 0
    except FileNotFoundError as err:
        print(err, file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as err:
        return err.returncode or 1


if __name__ == "__main__":
    sys.exit(main())
