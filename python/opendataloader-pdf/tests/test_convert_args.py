"""Mock-based tests for convert() argument handling (fast)"""

from unittest.mock import patch

import pytest
import opendataloader_pdf


def get_args(mock_run_jar):
    """Helper to extract CLI args from mock"""
    args = mock_run_jar.call_args[0][0]
    return args


# --- Format argument tests ---
@pytest.mark.parametrize(
    "fmt",
    [
        "json",
        "text",
        "html",
        "pdf",
        "markdown",
        "markdown-with-html",
        "markdown-with-images",
    ],
)
def test_format(input_pdf, fmt):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format=fmt)
        args = get_args(mock_run_jar)
        assert "--format" in args
        assert fmt in args


def test_format_as_list(input_pdf):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format=["json", "text"])
        args = get_args(mock_run_jar)
        assert "--format" in args
        assert "json,text" in args


# --- Input path argument tests ---
def test_input_path_as_list(input_pdf):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=[str(input_pdf)], format="json")
        args = get_args(mock_run_jar)
        assert str(input_pdf) in args


# --- Option argument tests ---
@pytest.mark.parametrize(
    "kwarg,cli_flag,value",
    [
        ({"keep_line_breaks": True}, "--keep-line-breaks", None),
        ({"replace_invalid_chars": " "}, "--replace-invalid-chars", " "),
        ({"use_struct_tree": True}, "--use-struct-tree", None),
        ({"reading_order": "bbox"}, "--reading-order", "bbox"),
        ({"table_method": "cluster"}, "--table-method", "cluster"),
        ({"table_method": ["cluster"]}, "--table-method", "cluster"),
        (
            {"content_safety_off": "hidden-text,tiny"},
            "--content-safety-off",
            "hidden-text,tiny",
        ),
        (
            {"content_safety_off": ["hidden-text", "tiny"]},
            "--content-safety-off",
            "hidden-text,tiny",
        ),
    ],
)
def test_option_args(input_pdf, kwarg, cli_flag, value):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format="json", **kwarg)
        args = get_args(mock_run_jar)
        assert cli_flag in args
        if value:
            assert value in args


# --- Page separator tests ---
@pytest.mark.parametrize(
    "kwarg,cli_flag,value",
    [
        ({"markdown_page_separator": "---"}, "--markdown-page-separator", "---"),
        ({"text_page_separator": "---"}, "--text-page-separator", "---"),
        ({"html_page_separator": "<hr/>"}, "--html-page-separator", "<hr/>"),
    ],
)
def test_page_separator(input_pdf, kwarg, cli_flag, value):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format="json", **kwarg)
        args = get_args(mock_run_jar)
        assert cli_flag in args
        assert value in args


# --- Embed images tests ---
def test_embed_images(input_pdf):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format="json", embed_images=True)
        args = get_args(mock_run_jar)
        assert "--embed-images" in args


@pytest.mark.parametrize("fmt", ["png", "jpeg"])
def test_image_format(input_pdf, fmt):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(input_path=str(input_pdf), format="json", image_format=fmt)
        args = get_args(mock_run_jar)
        assert "--image-format" in args
        assert fmt in args


def test_embed_images_with_format(input_pdf):
    with patch("opendataloader_pdf.wrapper.run_jar") as mock_run_jar:
        opendataloader_pdf.convert(
            input_path=str(input_pdf),
            format="json",
            embed_images=True,
            image_format="jpeg",
        )
        args = get_args(mock_run_jar)
        assert "--embed-images" in args
        assert "--image-format" in args
        assert "jpeg" in args
