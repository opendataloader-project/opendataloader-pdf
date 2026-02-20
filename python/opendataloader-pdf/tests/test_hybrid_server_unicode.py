"""Tests for Unicode sanitization in hybrid server responses.

Validates that lone surrogates and null characters from Docling OCR output
are sanitized before JSON serialization to prevent UnicodeEncodeError in
Starlette's JSONResponse.render().
"""

import json

import pytest

from opendataloader_pdf.hybrid_server import sanitize_unicode


class TestSanitizeUnicode:
    """Tests for the sanitize_unicode function."""

    def test_lone_surrogate_replaced(self):
        """Lone surrogates should be replaced with U+FFFD."""
        data = {"text": "Hello \ud800 World"}
        result = sanitize_unicode(data)
        assert "\ud800" not in result["text"]
        assert "\ufffd" in result["text"]

    def test_all_surrogate_range_replaced(self):
        """All surrogate code points (U+D800 to U+DFFF) should be replaced."""
        data = {"text": "\ud800\udbff\udc00\udfff"}
        result = sanitize_unicode(data)
        assert result["text"] == "\ufffd" * 4

    def test_null_character_replaced(self):
        """Null characters should be replaced with U+FFFD."""
        data = {"text": "Hello\x00World"}
        result = sanitize_unicode(data)
        assert "\x00" not in result["text"]
        assert result["text"] == "Hello\ufffdWorld"

    def test_nested_dict_sanitized(self):
        """Nested dictionaries should be sanitized recursively."""
        data = {"level1": {"level2": {"text": "bad\ud800char"}}}
        result = sanitize_unicode(data)
        assert "\ud800" not in result["level1"]["level2"]["text"]
        assert "\ufffd" in result["level1"]["level2"]["text"]

    def test_list_sanitized(self):
        """Lists within the data should be sanitized."""
        data = {"items": ["good", "bad\ud800text", "also\x00bad"]}
        result = sanitize_unicode(data)
        assert result["items"][0] == "good"
        assert "\ud800" not in result["items"][1]
        assert "\x00" not in result["items"][2]

    def test_clean_data_unchanged(self):
        """Clean data without problematic characters should pass through unchanged."""
        data = {"text": "Hello World", "number": 42, "flag": True, "nothing": None}
        result = sanitize_unicode(data)
        assert result == data

    def test_non_string_values_preserved(self):
        """Non-string values (int, float, bool, None) should be preserved as-is."""
        data = {"int": 42, "float": 3.14, "bool": True, "none": None}
        result = sanitize_unicode(data)
        assert result == data

    def test_sanitized_output_json_serializable(self):
        """Sanitized output must survive json.dumps + encode('utf-8') without error."""
        data = {
            "status": "success",
            "document": {
                "json_content": {
                    "body": {"text": "OCR text with \ud800 lone surrogate and \x00 null"}
                }
            },
        }
        result = sanitize_unicode(data)
        # This is the exact operation that Starlette's JSONResponse.render() performs
        json_bytes = json.dumps(result, ensure_ascii=False).encode("utf-8")
        assert isinstance(json_bytes, bytes)

    def test_mixed_valid_and_invalid_unicode(self):
        """Valid Unicode (including CJK, emoji) should be preserved alongside sanitization."""
        data = {"text": "Valid \u4e16\u754c \ud800 text"}
        result = sanitize_unicode(data)
        assert "\u4e16\u754c" in result["text"]  # CJK preserved
        assert "\ud800" not in result["text"]  # surrogate removed
        assert "\ufffd" in result["text"]  # replacement added
