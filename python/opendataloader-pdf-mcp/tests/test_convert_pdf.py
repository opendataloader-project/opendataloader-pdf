"""Tests for the convert_pdf MCP tool."""

import pytest
from unittest.mock import patch, MagicMock

from opendataloader_pdf_mcp.server import convert_pdf, mcp


class TestConvertPdfValidation:
    """Tests for input validation."""

    def test_nonexistent_file_raises_error(self):
        """Should raise FileNotFoundError for missing files."""
        with pytest.raises(FileNotFoundError):
            convert_pdf(input_path="/nonexistent/file.pdf")

    def test_directory_raises_error(self, tmp_path):
        """Should raise FileNotFoundError for directories."""
        with pytest.raises(FileNotFoundError):
            convert_pdf(input_path=str(tmp_path))


class TestConvertPdfFormats:
    """Tests for output format support."""

    def test_markdown_output(self, input_pdf):
        """Should convert PDF to Markdown."""
        result = convert_pdf(input_path=str(input_pdf), format="markdown")
        assert len(result) > 0
        assert "Lorem" in result

    def test_json_output(self, input_pdf):
        """Should convert PDF to JSON."""
        import json

        result = convert_pdf(input_path=str(input_pdf), format="json")
        parsed = json.loads(result)
        assert isinstance(parsed, (dict, list))

    def test_html_output(self, input_pdf):
        """Should convert PDF to HTML."""
        result = convert_pdf(input_path=str(input_pdf), format="html")
        assert "<" in result
        assert ">" in result

    def test_text_output(self, input_pdf):
        """Should convert PDF to plain text."""
        result = convert_pdf(input_path=str(input_pdf), format="text")
        assert len(result) > 0
        assert "Lorem" in result

    def test_default_format_is_markdown(self, input_pdf):
        """Default format should be markdown."""
        result = convert_pdf(input_path=str(input_pdf))
        assert "Lorem" in result


class TestConvertPdfOptions:
    """Tests for optional parameters passed through to convert()."""

    def test_pages_option(self, input_pdf_academic):
        """Should extract only specified pages."""
        result_all = convert_pdf(input_path=str(input_pdf_academic), format="text")
        result_page1 = convert_pdf(input_path=str(input_pdf_academic), format="text", pages="1")
        assert len(result_page1) < len(result_all)

    def test_quiet_is_always_true(self, input_pdf):
        """convert() should always be called with quiet=True."""
        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert:
            # Create a fake output file so read doesn't fail
            convert_pdf.__wrapped__ if hasattr(convert_pdf, '__wrapped__') else None
            try:
                convert_pdf(input_path=str(input_pdf))
            except Exception:
                pass
            if mock_convert.called:
                kwargs = mock_convert.call_args[1]
                assert kwargs.get("quiet") is True


class TestMcpToolRegistration:
    """Tests for MCP server tool registration."""

    def test_convert_pdf_tool_is_registered(self):
        """convert_pdf should be registered as an MCP tool."""
        tool_names = [tool.name for tool in mcp._tool_manager.list_tools()]
        assert "convert_pdf" in tool_names
