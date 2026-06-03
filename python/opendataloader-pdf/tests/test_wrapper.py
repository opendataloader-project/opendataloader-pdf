"""Tests for wrapper.py: to-stdout validation and batch_convert."""

import subprocess
import warnings
from unittest.mock import MagicMock, patch

import pytest

import opendataloader_pdf
from opendataloader_pdf.wrapper import batch_convert, convert


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_run_jar_mock(monkeypatch, side_effect=None):
    """Patch run_jar so no real JAR or Java is needed.

    convert_generated imports run_jar at module load time, so we patch
    the reference held inside convert_generated, not the runner module.
    """
    mock = MagicMock(return_value="")
    if side_effect:
        mock.side_effect = side_effect
    monkeypatch.setattr("opendataloader_pdf.convert_generated.run_jar", mock)
    return mock


# ---------------------------------------------------------------------------
# Issue #492 — to-stdout + unsupported format raises ValueError early
# ---------------------------------------------------------------------------

class TestToStdoutValidation:
    """convert() must raise ValueError before calling the JAR when --to-stdout
    is combined with a format that the JAR does not support for stdout."""

    def test_to_stdout_json_raises(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="json"):
            convert(input_path="a.pdf", to_stdout=True, format="json")
        mock.assert_not_called()

    def test_to_stdout_html_raises(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="html"):
            convert(input_path="a.pdf", to_stdout=True, format="html")
        mock.assert_not_called()

    def test_to_stdout_pdf_raises(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="pdf"):
            convert(input_path="a.pdf", to_stdout=True, format="pdf")
        mock.assert_not_called()

    def test_to_stdout_tagged_pdf_raises(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="tagged-pdf"):
            convert(input_path="a.pdf", to_stdout=True, format="tagged-pdf")
        mock.assert_not_called()

    def test_to_stdout_markdown_allowed(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        convert(input_path="a.pdf", to_stdout=True, format="markdown")
        mock.assert_called_once()

    def test_to_stdout_text_allowed(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        convert(input_path="a.pdf", to_stdout=True, format="text")
        mock.assert_called_once()

    def test_json_without_to_stdout_allowed(self, monkeypatch):
        """format=json without to_stdout must not raise."""
        mock = _make_run_jar_mock(monkeypatch)
        convert(input_path="a.pdf", format="json")
        mock.assert_called_once()

    def test_error_message_names_supported_formats(self, monkeypatch):
        _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="text, markdown"):
            convert(input_path="a.pdf", to_stdout=True, format="json")

    def test_to_stdout_comma_separated_format_list(self, monkeypatch):
        """A comma-joined format string containing an unsupported value raises."""
        mock = _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="json"):
            convert(input_path="a.pdf", to_stdout=True, format="json,text")
        mock.assert_not_called()


# ---------------------------------------------------------------------------
# Issue #451 — batch_convert() with per-file error tolerance
# ---------------------------------------------------------------------------

class TestBatchConvert:
    """batch_convert() should iterate per-file and handle errors per errors=."""

    def test_invalid_errors_param_raises(self, monkeypatch):
        _make_run_jar_mock(monkeypatch)
        with pytest.raises(ValueError, match="errors must be"):
            batch_convert(["a.pdf"], errors="skip")

    def test_all_success_returns_none_values(self, monkeypatch):
        _make_run_jar_mock(monkeypatch)
        result = batch_convert(["a.pdf", "b.pdf"], format="text")
        assert result == {"a.pdf": None, "b.pdf": None}

    def test_errors_raise_reraises_on_first_failure(self, monkeypatch):
        err = subprocess.CalledProcessError(1, ["java"])
        call_count = 0

        def fake_run_jar(args, quiet=False):
            nonlocal call_count
            call_count += 1
            if "b.pdf" in args:
                raise err

        monkeypatch.setattr("opendataloader_pdf.convert_generated.run_jar", fake_run_jar)
        with pytest.raises(subprocess.CalledProcessError):
            batch_convert(["a.pdf", "b.pdf", "c.pdf"], errors="raise")
        # c.pdf must never be attempted after b.pdf fails
        assert call_count == 2

    def test_errors_ignore_continues_and_records_exception(self, monkeypatch):
        err = subprocess.CalledProcessError(1, ["java"])

        def fake_run_jar(args, quiet=False):
            if "bad.pdf" in args:
                raise err

        monkeypatch.setattr("opendataloader_pdf.convert_generated.run_jar", fake_run_jar)
        result = batch_convert(["good.pdf", "bad.pdf", "also_good.pdf"], errors="ignore")
        assert result["good.pdf"] is None
        assert result["bad.pdf"] is err
        assert result["also_good.pdf"] is None

    def test_errors_ignore_emits_warning(self, monkeypatch):
        err = subprocess.CalledProcessError(1, ["java"])

        def fake_run_jar(args, quiet=False):
            if "bad.pdf" in args:
                raise err

        monkeypatch.setattr("opendataloader_pdf.convert_generated.run_jar", fake_run_jar)
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            batch_convert(["bad.pdf"], errors="ignore")
        assert any("bad.pdf" in str(w.message) for w in caught)
        assert any(issubclass(w.category, RuntimeWarning) for w in caught)

    def test_kwargs_forwarded_to_convert(self, monkeypatch):
        mock = _make_run_jar_mock(monkeypatch)
        batch_convert(["a.pdf"], format="text", quiet=True)
        call_args = mock.call_args[0][0]
        assert "--format" in call_args
        assert "text" in call_args
        assert "--quiet" in call_args

    def test_batch_convert_exported_from_package(self):
        """batch_convert must be importable directly from opendataloader_pdf."""
        assert hasattr(opendataloader_pdf, "batch_convert")
        assert callable(opendataloader_pdf.batch_convert)
