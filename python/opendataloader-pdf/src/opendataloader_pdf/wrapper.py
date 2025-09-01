import subprocess
import sys
import importlib_resources
from pathlib import Path

# The consistent name of the JAR file bundled with the package
_JAR_NAME = "opendataloader-pdf-cli.jar"


def run(
    input_path: str,
    output_folder: str = None,
    password: str = None,
    to_markdown: bool = False,
    to_annotated_pdf: bool = False,
    keep_line_breaks: bool = False,
    find_hidden_text: bool = False,
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
        to_markdown: If True, generates a Markdown output file.
        to_annotated_pdf: If True, generates an annotated PDF output file.
        keep_line_breaks: If True, keeps line breaks in the output.
        find_hidden_text: If True, finds hidden text in the PDF.
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
        args.extend(["--folder", output_folder])
    if password:
        args.extend(["--password", password])
    if to_markdown:
        args.append("--markdown")
    if to_annotated_pdf:
        args.append("--pdf")
    if keep_line_breaks:
        args.append("--keeplinebreaks")
    if find_hidden_text:
        args.append("--findhiddentext")
    if html_in_markdown:
        args.append("--htmlinmarkdown")
    if add_image_to_markdown:
        args.append("--addimagetomarkdown")

    args.append(input_path)

    try:
        # Find the JAR file within the package
        jar_ref = importlib_resources.files("opendataloader_pdf").joinpath(
            "jar", _JAR_NAME
        )
        with importlib_resources.as_file(jar_ref) as jar_path:
            command = ["java", "-jar", str(jar_path)] + args

            if debug:
                print(f"Running command: {' '.join(command)}", file=sys.stderr)
                process = subprocess.Popen(
                    command,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
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
                    encoding="utf-8",
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
