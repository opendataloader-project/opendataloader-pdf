"""Tests for the convert_pdf MCP tool."""

import pytest
from unittest.mock import patch

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

    def test_unsupported_format_raises_error(self, input_pdf):
        """Should raise ValueError for unsupported formats."""
        with pytest.raises(ValueError, match="Unsupported format"):
            convert_pdf(input_path=str(input_pdf), format="docx")

    def test_markdown_with_html_format_value_raises_error(self, input_pdf):
        """Removed format value 'markdown-with-html' should raise ValueError."""
        with pytest.raises(ValueError, match="markdown-with-html"):
            convert_pdf(input_path=str(input_pdf), format="markdown-with-html")

    def test_markdown_with_images_format_value_raises_error(self, input_pdf):
        """Removed format value 'markdown-with-images' should raise ValueError."""
        with pytest.raises(ValueError, match="markdown-with-images"):
            convert_pdf(input_path=str(input_pdf), format="markdown-with-images")


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

    def test_quiet_is_always_true(self, input_pdf, tmp_path):
        """convert() should always be called with quiet=True."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            result = convert_pdf(input_path=str(input_pdf))
            mock_convert.assert_called_once()
            kwargs = mock_convert.call_args[1]
            assert kwargs["quiet"] is True

    def test_markdown_with_html_param_forwarded(self, input_pdf, tmp_path):
        """markdown_with_html=True should pass markdown_with_html=True to convert()."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="markdown", markdown_with_html=True)
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("markdown_with_html") is True

    def test_markdown_format_defaults_image_output_to_embedded(self, input_pdf, tmp_path):
        """format='markdown' without explicit image_output should default to embedded."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="markdown")
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("image_output") == "embedded"

    def test_html_format_defaults_image_output_to_embedded(self, input_pdf, tmp_path):
        """format='html' without explicit image_output should default to embedded."""
        fake_output = tmp_path / "lorem.html"
        fake_output.write_text("<html>mocked</html>")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="html")
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("image_output") == "embedded"

    def test_explicit_image_output_overrides_default(self, input_pdf, tmp_path):
        """Explicit image_output should override the embedded default."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="markdown", image_output="off")
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("image_output") == "off"

    def test_json_format_does_not_set_image_output_default(self, input_pdf, tmp_path):
        """format='json' without explicit image_output should not set image_output."""
        fake_output = tmp_path / "lorem.json"
        fake_output.write_text("{}")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="json")
            kwargs = mock_convert.call_args[1]
            assert "image_output" not in kwargs


class TestMcpToolRegistration:
    """Tests for MCP server tool registration."""

    def test_convert_pdf_tool_is_registered(self):
        """convert_pdf should be registered as an MCP tool."""
        # FastMCP does not expose a public tool-listing API as of v1.x;
        # _tool_manager is the only way to introspect registered tools.
        tool_names = [tool.name for tool in mcp._tool_manager.list_tools()]
        assert "convert_pdf" in tool_names
