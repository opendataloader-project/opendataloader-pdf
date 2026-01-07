# AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
# Run `npm run generate-options` to regenerate

"""
Auto-generated convert function for opendataloader-pdf.
"""
from typing import List, Optional, Union

from .runner import run_jar


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
    table_method: Optional[str] = None,
    reading_order: Optional[str] = None,
    markdown_page_separator: Optional[str] = None,
    text_page_separator: Optional[str] = None,
    html_page_separator: Optional[str] = None,
    image_output: Optional[str] = None,
    image_format: Optional[str] = None,
    image_dir: Optional[str] = None,
    pages: Optional[str] = None,
    hybrid: Optional[str] = None,
    hybrid_url: Optional[str] = None,
    hybrid_timeout: Optional[str] = None,
    hybrid_fallback: bool = True,
) -> None:
    """
    Convert PDF(s) into the requested output format(s).

    Args:
        input_path: One or more input PDF file paths or directories
        output_dir: Directory where output files are written. Default: input file directory
        password: Password for encrypted PDF files
        format: Output formats (comma-separated). Values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images. Default: json
        quiet: Suppress console logging output
        content_safety_off: Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg
        keep_line_breaks: Preserve original line breaks in extracted text
        replace_invalid_chars: Replacement character for invalid/unrecognized characters. Default: space
        use_struct_tree: Use PDF structure tree (tagged PDF) for reading order and semantic structure
        table_method: Table detection method. Values: default (border-based), cluster (border + cluster). Default: default
        reading_order: Reading order algorithm. Values: off, xycut. Default: xycut
        markdown_page_separator: Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none
        text_page_separator: Separator between pages in text output. Use %page-number% for page numbers. Default: none
        html_page_separator: Separator between pages in HTML output. Use %page-number% for page numbers. Default: none
        image_output: Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external
        image_format: Output format for extracted images. Values: png, jpeg. Default: png
        image_dir: Directory for extracted images. Default: {output-dir}/{pdf-name}_images
        pages: Pages to extract (e.g., "1,3,5-7"). Default: all pages
        hybrid: Hybrid backend for AI processing. Values: off (default), docling-fast
        hybrid_url: Hybrid backend server URL (overrides default)
        hybrid_timeout: Hybrid backend request timeout in milliseconds. Default: 30000
        hybrid_fallback: Fallback to Java processing on hybrid backend error. Default: true
    """
    args: List[str] = []

    # Build input paths
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
            if format:
                args.extend(["--format", ",".join(format)])
        else:
            args.extend(["--format", format])
    if quiet:
        args.append("--quiet")
    if content_safety_off:
        if isinstance(content_safety_off, list):
            if content_safety_off:
                args.extend(["--content-safety-off", ",".join(content_safety_off)])
        else:
            args.extend(["--content-safety-off", content_safety_off])
    if keep_line_breaks:
        args.append("--keep-line-breaks")
    if replace_invalid_chars:
        args.extend(["--replace-invalid-chars", replace_invalid_chars])
    if use_struct_tree:
        args.append("--use-struct-tree")
    if table_method:
        args.extend(["--table-method", table_method])
    if reading_order:
        args.extend(["--reading-order", reading_order])
    if markdown_page_separator:
        args.extend(["--markdown-page-separator", markdown_page_separator])
    if text_page_separator:
        args.extend(["--text-page-separator", text_page_separator])
    if html_page_separator:
        args.extend(["--html-page-separator", html_page_separator])
    if image_output:
        args.extend(["--image-output", image_output])
    if image_format:
        args.extend(["--image-format", image_format])
    if image_dir:
        args.extend(["--image-dir", image_dir])
    if pages:
        args.extend(["--pages", pages])
    if hybrid:
        args.extend(["--hybrid", hybrid])
    if hybrid_url:
        args.extend(["--hybrid-url", hybrid_url])
    if hybrid_timeout:
        args.extend(["--hybrid-timeout", hybrid_timeout])
    if hybrid_fallback:
        args.append("--hybrid-fallback")

    run_jar(args, quiet)
