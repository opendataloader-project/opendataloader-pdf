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

    @pytest.mark.parametrize(
        "deprecated_format", ["markdown-with-html", "markdown-with-images"]
    )
    def test_deprecated_format_values_are_rejected(self, input_pdf, deprecated_format):
        """PDFDLOSP-6: legacy --format values were retired from the MCP surface."""
        with pytest.raises(ValueError, match="Unsupported format"):
            convert_pdf(input_path=str(input_pdf), format=deprecated_format)


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

    def test_markdown_with_html_flag_is_forwarded(self, input_pdf, tmp_path):
        """markdown_with_html=True should map to the convert() kwarg of the same name."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), markdown_with_html=True)
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("markdown_with_html") is True

    def test_markdown_with_html_default_is_not_forwarded(self, input_pdf, tmp_path):
        """The default markdown_with_html=False must not appear in the kwargs sent to convert()."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="markdown")
            kwargs = mock_convert.call_args[1]
            assert "markdown_with_html" not in kwargs

    @pytest.mark.parametrize("fmt", ["markdown", "html"])
    def test_markdown_and_html_default_to_embedded_images(self, input_pdf, tmp_path, fmt):
        """Markdown / HTML output should embed images by default so they survive the temp dir."""
        fake_output = tmp_path / f"lorem.{'md' if fmt == 'markdown' else 'html'}"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format=fmt)
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("image_output") == "embedded"

    def test_explicit_image_output_overrides_default(self, input_pdf, tmp_path):
        """An explicit image_output value must not be overridden by the markdown default."""
        fake_output = tmp_path / "lorem.md"
        fake_output.write_text("mocked")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="markdown", image_output="off")
            kwargs = mock_convert.call_args[1]
            assert kwargs.get("image_output") == "off"

    def test_json_format_does_not_set_default_image_output(self, input_pdf, tmp_path):
        """JSON/text output should not get the markdown-only embedded-image default."""
        fake_output = tmp_path / "lorem.json"
        fake_output.write_text("{}")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf.convert") as mock_convert, \
             patch("opendataloader_pdf_mcp.server.tempfile.TemporaryDirectory") as mock_tmpdir:
            mock_tmpdir.return_value.__enter__ = lambda self: str(tmp_path)
            mock_tmpdir.return_value.__exit__ = lambda *args: None
            convert_pdf(input_path=str(input_pdf), format="json")
            kwargs = mock_convert.call_args[1]
            assert "image_output" not in kwargs


class TestConvertPdfImageEmbedding:
    """End-to-end checks that markdown/html output stays self-contained.

    Regression coverage for #539: the MCP server returns a single string, so
    `image_output=external` would leave the Markdown referencing files in a
    temp dir that's already been deleted.
    """

    def test_markdown_embeds_images_as_base64_by_default(self, input_pdf_with_images):
        """First page of the academic sample has images; output must inline them."""
        result = convert_pdf(
            input_path=str(input_pdf_with_images), format="markdown", pages="1"
        )
        assert "![" in result, "expected markdown image syntax in output"
        assert "data:image/" in result and ";base64," in result, (
            "expected base64-embedded image data URI in markdown output"
        )

    def test_image_output_off_strips_images(self, input_pdf_with_images):
        """Explicit image_output='off' must override the embedded default."""
        result = convert_pdf(
            input_path=str(input_pdf_with_images),
            format="markdown",
            pages="1",
            image_output="off",
        )
        assert "data:image/" not in result, (
            "image_output='off' should not emit base64 image data URIs"
        )


class TestMcpToolRegistration:
    """Tests for MCP server tool registration."""

    def test_convert_pdf_tool_is_registered(self):
        """convert_pdf should be registered as an MCP tool."""
        # FastMCP does not expose a public tool-listing API as of v1.x;
        # _tool_manager is the only way to introspect registered tools.
        tool_names = [tool.name for tool in mcp._tool_manager.list_tools()]
        assert "convert_pdf" in tool_names
