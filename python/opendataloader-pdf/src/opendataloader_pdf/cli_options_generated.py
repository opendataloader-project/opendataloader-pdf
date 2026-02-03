# AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
# Run `npm run generate-options` to regenerate

"""
CLI option definitions for opendataloader-pdf.
"""
from typing import Any, Dict, List


# Option metadata list
CLI_OPTIONS: List[Dict[str, Any]] = [
    {
        "name": "output-dir",
        "python_name": "output_dir",
        "short_name": "o",
        "type": "string",
        "required": False,
        "default": None,
        "description": "Directory where output files are written. Default: input file directory",
    },
    {
        "name": "password",
        "python_name": "password",
        "short_name": "p",
        "type": "string",
        "required": False,
        "default": None,
        "description": "Password for encrypted PDF files",
    },
    {
        "name": "format",
        "python_name": "format",
        "short_name": "f",
        "type": "string",
        "required": False,
        "default": None,
        "description": "Output formats (comma-separated). Values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images. Default: json",
    },
    {
        "name": "quiet",
        "python_name": "quiet",
        "short_name": "q",
        "type": "boolean",
        "required": False,
        "default": False,
        "description": "Suppress console logging output",
    },
    {
        "name": "content-safety-off",
        "python_name": "content_safety_off",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg",
    },
    {
        "name": "keep-line-breaks",
        "python_name": "keep_line_breaks",
        "short_name": None,
        "type": "boolean",
        "required": False,
        "default": False,
        "description": "Preserve original line breaks in extracted text",
    },
    {
        "name": "replace-invalid-chars",
        "python_name": "replace_invalid_chars",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": " ",
        "description": "Replacement character for invalid/unrecognized characters. Default: space",
    },
    {
        "name": "use-struct-tree",
        "python_name": "use_struct_tree",
        "short_name": None,
        "type": "boolean",
        "required": False,
        "default": False,
        "description": "Use PDF structure tree (tagged PDF) for reading order and semantic structure",
    },
    {
        "name": "table-method",
        "python_name": "table_method",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "default",
        "description": "Table detection method. Values: default (border-based), cluster (border + cluster). Default: default",
    },
    {
        "name": "reading-order",
        "python_name": "reading_order",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "xycut",
        "description": "Reading order algorithm. Values: off, xycut. Default: xycut",
    },
    {
        "name": "markdown-page-separator",
        "python_name": "markdown_page_separator",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Separator between pages in Markdown output. Use %%page-number%% for page numbers. Default: none",
    },
    {
        "name": "text-page-separator",
        "python_name": "text_page_separator",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Separator between pages in text output. Use %%page-number%% for page numbers. Default: none",
    },
    {
        "name": "html-page-separator",
        "python_name": "html_page_separator",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Separator between pages in HTML output. Use %%page-number%% for page numbers. Default: none",
    },
    {
        "name": "image-output",
        "python_name": "image_output",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "external",
        "description": "Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external",
    },
    {
        "name": "image-format",
        "python_name": "image_format",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "png",
        "description": "Output format for extracted images. Values: png, jpeg. Default: png",
    },
    {
        "name": "image-dir",
        "python_name": "image_dir",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Directory for extracted images",
    },
    {
        "name": "pages",
        "python_name": "pages",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Pages to extract (e.g., \"1,3,5-7\"). Default: all pages",
    },
    {
        "name": "include-header-footer",
        "python_name": "include_header_footer",
        "short_name": None,
        "type": "boolean",
        "required": False,
        "default": False,
        "description": "Include page headers and footers in output",
    },
    {
        "name": "hybrid",
        "python_name": "hybrid",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "off",
        "description": "Hybrid backend for AI processing. Values: off (default), docling-fast",
    },
    {
        "name": "hybrid-mode",
        "python_name": "hybrid_mode",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "auto",
        "description": "Hybrid triage mode. Values: auto (default, dynamic triage), full (skip triage, all pages to backend)",
    },
    {
        "name": "hybrid-url",
        "python_name": "hybrid_url",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": None,
        "description": "Hybrid backend server URL (overrides default)",
    },
    {
        "name": "hybrid-timeout",
        "python_name": "hybrid_timeout",
        "short_name": None,
        "type": "string",
        "required": False,
        "default": "30000",
        "description": "Hybrid backend request timeout in milliseconds. Default: 30000",
    },
    {
        "name": "hybrid-fallback",
        "python_name": "hybrid_fallback",
        "short_name": None,
        "type": "boolean",
        "required": False,
        "default": True,
        "description": "Fallback to Java processing on hybrid backend error. Default: true",
    },
]


def add_options_to_parser(parser) -> None:
    """Add all CLI options to an argparse.ArgumentParser."""
    for opt in CLI_OPTIONS:
        flags = []
        if opt["short_name"]:
            flags.append(f'-{opt["short_name"]}')
        flags.append(f'--{opt["name"]}')

        kwargs = {"help": opt["description"]}
        if opt["type"] == "boolean":
            kwargs["action"] = "store_true"
        else:
            kwargs["default"] = None

        parser.add_argument(*flags, **kwargs)
