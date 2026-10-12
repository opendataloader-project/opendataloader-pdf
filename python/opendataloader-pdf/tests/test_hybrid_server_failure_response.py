"""What a failed conversion tells the caller.

The CLI logs the 500 body once as a warning, so the body is the one place a
user without the server console sees why a conversion failed. It has to name
the cause, and for the one failure we have met in the field (issue #725) say
what fixes it. It must not carry the exception text itself: a parser error
can quote offsets and object data from the uploaded file.
"""

import pytest

from opendataloader_pdf import hybrid_server

NON_ASCII_ENV_PATH_ERROR = (
    "filesystem error: Cannot convert character sequence: Illegal byte sequence"
)


def _post_failing_pdf(monkeypatch, exc, endpoint="/v1/convert/file"):
    """POST one PDF through a real app whose converter raises `exc`."""
    from fastapi.testclient import TestClient

    class _Converter:
        def convert(self, path, **kwargs):
            raise exc

    app = hybrid_server.create_app()
    monkeypatch.setattr(hybrid_server, "converter", _Converter())
    monkeypatch.setattr(hybrid_server, "create_converter", lambda **kw: _Converter())
    monkeypatch.setattr(hybrid_server, "_probe_page_count", lambda path: 1)

    # Not entered as a context manager: the lifespan would load real models.
    return TestClient(app).post(
        endpoint,
        files={"files": ("x.pdf", b"%PDF-1.4\n", "application/pdf")},
    )


def test_failure_names_the_exception_type(monkeypatch):
    response = _post_failing_pdf(monkeypatch, ValueError("bad xref at offset 1234"))
    assert response.status_code == 500
    body = response.json()
    assert body["status"] == "failure"
    assert "ValueError" in body["errors"][0]


def test_failure_does_not_quote_the_exception(monkeypatch):
    response = _post_failing_pdf(monkeypatch, ValueError("bad xref at offset 1234"))
    assert "offset 1234" not in response.text


def test_non_ascii_env_path_failure_carries_the_remedy(monkeypatch):
    response = _post_failing_pdf(monkeypatch, RuntimeError(NON_ASCII_ENV_PATH_ERROR))
    assert response.status_code == 500
    errors = " ".join(response.json()["errors"])
    assert "docling-parse" in errors
    assert "7.22.2" in errors
    assert "LOCALAPPDATA" in errors


def _as_docling_raises_it(root):
    """Wrap `root` the way docling's DocumentConverter.convert() surfaces a
    backend failure: a ConversionError with a generic message, the original
    exception reachable only through __cause__ (InputDocument.__init__ records
    it as the rejection's original_error)."""
    from docling.exceptions import ConversionError

    exc = ConversionError(
        "Conversion failed for: x.pdf with status: failure. Errors: An unexpected "
        "error occurred while opening the document x.pdf."
    )
    exc.__cause__ = root
    return exc


def test_remedy_is_found_through_doclings_conversion_error(monkeypatch):
    response = _post_failing_pdf(
        monkeypatch, _as_docling_raises_it(RuntimeError(NON_ASCII_ENV_PATH_ERROR))
    )
    assert response.status_code == 500
    assert "LOCALAPPDATA" in " ".join(response.json()["errors"])


def test_a_known_cause_does_not_point_at_the_logs(monkeypatch):
    response = _post_failing_pdf(
        monkeypatch, _as_docling_raises_it(RuntimeError(NON_ASCII_ENV_PATH_ERROR))
    )
    assert "server logs" not in response.text


def test_an_unknown_cause_still_points_at_the_logs(monkeypatch):
    response = _post_failing_pdf(monkeypatch, ValueError("bad xref"))
    assert "Check server logs for details." in response.json()["errors"][0]


def test_failure_names_the_root_cause_behind_doclings_wrapper(monkeypatch):
    response = _post_failing_pdf(
        monkeypatch, _as_docling_raises_it(RuntimeError(NON_ASCII_ENV_PATH_ERROR))
    )
    first = response.json()["errors"][0]
    assert "ConversionError" in first
    assert "RuntimeError" in first


def test_remedy_survives_the_real_docling_wrapping(monkeypatch, tmp_path):
    """Drives the real DocumentConverter with the parser constructor failing
    as in #725, so this breaks if docling changes how it wraps the failure.
    No model is loaded: the backend fails before the pipeline is built."""
    from docling.datamodel.base_models import InputFormat
    from docling.datamodel.pipeline_options import PdfPipelineOptions
    from docling.document_converter import DocumentConverter, PdfFormatOption
    from docling_parse import pdf_parser

    def boom(self, *args, **kwargs):
        raise RuntimeError(NON_ASCII_ENV_PATH_ERROR)

    monkeypatch.setattr(pdf_parser.DoclingThreadedPdfParser, "__init__", boom)
    monkeypatch.setattr(pdf_parser.DoclingPdfParser, "__init__", boom)
    pdf = tmp_path / "x.pdf"
    pdf.write_bytes(b"%PDF-1.4\n%%EOF\n")
    converter = DocumentConverter(
        allowed_formats=[InputFormat.PDF],
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=PdfPipelineOptions(do_ocr=False))
        },
    )
    with pytest.raises(Exception) as info:
        converter.convert(pdf)
    assert "LOCALAPPDATA" in " ".join(hybrid_server._failure_errors(info.value))


def test_conversion_phrase_outside_a_filesystem_error_gets_no_remedy(monkeypatch):
    """The phrase alone is generic libstdc++ wording; only the filesystem
    error form has been seen in #725."""
    response = _post_failing_pdf(
        monkeypatch,
        RuntimeError("Cannot convert character sequence: Illegal byte sequence"),
    )
    errors = response.json()["errors"]
    assert errors == ["PDF conversion failed (RuntimeError). Check server logs for details."]


def test_exception_chain_walk_stops_on_a_cycle():
    exc = ValueError("loops")
    exc.__cause__ = exc
    assert hybrid_server._failure_errors(exc)[0].startswith("PDF conversion failed (ValueError)")


def test_an_unrelated_failure_carries_no_remedy(monkeypatch):
    response = _post_failing_pdf(monkeypatch, ValueError("bad xref"))
    assert "LOCALAPPDATA" not in response.text


def test_profile_failure_names_the_exception_type(monkeypatch):
    response = _post_failing_pdf(
        monkeypatch, RuntimeError(NON_ASCII_ENV_PATH_ERROR), endpoint="/v1/profile/file"
    )
    assert response.status_code == 500
    errors = " ".join(response.json()["errors"])
    assert "RuntimeError" in errors
    assert "LOCALAPPDATA" in errors
