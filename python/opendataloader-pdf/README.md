# Opendataloader PDF Python Wrapper

This package is a Python wrapper for the `opendataloader-pdf` Java command-line tool.

It allows you to process PDF files and convert them to JSON or Markdown format directly from Python.

## Prerequisites

- Java 11 or higher must be installed and available in your system's PATH.

## Installation

```bash
pip install opendataloader-pdf
```

## Usage

Here is a basic example of how to use:

```python
import opendataloader_pdf

opendataloader_pdf.run("path/to/document.pdf", to_markdown=True)

# If you don’t specify an output_folder,
# the output data will be saved in the same directory as the input document.
```

## Function: `run()`

The main function to process PDFs.

### Parameters

- `input_path` (str): Path to the input PDF file or folder. **(Required)**
- `output_folder` (str, optional): Path to the output folder. Defaults to the input folder.
- `password` (str, optional): Password for the PDF file.
- `to_markdown` (bool, optional): If `True`, generates a Markdown output file. Defaults to `False`.
- `to_annotated_pdf` (bool, optional): If `True`, generates an annotated PDF output file. Defaults to `False`.
- `keep_line_breaks` (bool, optional): If `True`, keeps line breaks in the output. Defaults to `False`.
- `find_hidden_text` (bool, optional): If `True`, finds hidden text in the PDF. Defaults to `False`.
- `html_in_markdown` (bool, optional): If `True`, uses HTML in the Markdown output. Defaults to `False`.
- `add_image_to_markdown` (bool, optional): If `True`, adds images to the Markdown output. Defaults to `False`.
- `debug` (bool, optional): If `True`, prints all messages from the CLI to the console during execution. Defaults to `False`.
