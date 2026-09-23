"""Tests for ``--document-timeout`` in the hybrid server."""

import sys
from unittest.mock import patch

import pytest

from opendataloader_pdf import hybrid_server


def _capture_pipeline_options(**kwargs):
    captured = {}

    def fake_pdf_format_option(*, pipeline_options):
        captured["pipeline_options"] = pipeline_options
        return object()

    with patch(
        "docling.document_converter.DocumentConverter"
    ) as mock_dc, patch(
        "docling.document_converter.PdfFormatOption", side_effect=fake_pdf_format_option
    ):
        mock_dc.return_value = object()
        hybrid_server.create_converter(**kwargs)

    return captured["pipeline_options"]


def test_no_timeout_by_default():
    opts = _capture_pipeline_options()
    assert opts.document_timeout is None


def test_timeout_is_forwarded_to_docling():
    opts = _capture_pipeline_options(document_timeout=90.0)
    assert opts.document_timeout == 90.0


def test_zero_means_unlimited_not_instant():
    """docling aborts once elapsed time exceeds the limit, so 0 would abort
    every conversion after its first page batch.
    """
    opts = _capture_pipeline_options(document_timeout=0)
    assert opts.document_timeout is None


def _run_main(argv, monkeypatch):
    """Drive `main()` through argparse and return the `create_app` kwargs.

    `main()` ends in `uvicorn.run(...)`; patching `create_app` and uvicorn lets
    it return after the flag handling without starting a server, and hands back
    what the CLI actually resolved -- including whether the production parser
    still validates this flag.
    """
    captured = {}
    monkeypatch.setattr("sys.argv", ["opendataloader-pdf-hybrid", *argv])
    monkeypatch.setattr(hybrid_server, "_check_dependencies", lambda: None)
    monkeypatch.setattr(hybrid_server, "_check_ocr_engine_available", lambda e: (True, ""))

    def fake_create_app(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(hybrid_server, "create_app", fake_create_app)
    fake_uvicorn = type("FakeUvicorn", (), {"run": staticmethod(lambda *a, **k: None)})
    monkeypatch.setitem(sys.modules, "uvicorn", fake_uvicorn)

    hybrid_server.main()
    return captured


def test_cli_default_reaches_create_app(monkeypatch):
    assert _run_main([], monkeypatch)["document_timeout"] == 0.0


def test_cli_duration_reaches_create_app(monkeypatch):
    kwargs = _run_main(["--document-timeout", "90"], monkeypatch)
    assert kwargs["document_timeout"] == 90.0


@pytest.mark.parametrize("bad", ["-1", "nan", "inf"])
def test_cli_rejects_a_duration_it_cannot_enforce(bad, monkeypatch, capsys):
    """Negative aborts every conversion at once; `nan` and `inf` compare so
    that nothing is ever aborted, while startup reports a limit.

    Driving `main()` rather than a hand-built parser is what keeps this honest:
    swapping the production `type=` would otherwise go unnoticed.
    """
    with pytest.raises(SystemExit):
        _run_main(["--document-timeout", bad], monkeypatch)
    assert "--document-timeout must be a finite number >= 0" in capsys.readouterr().err


def test_create_app_forwards_the_timeout_to_the_converter(monkeypatch):
    """The step between the CLI and docling that the two tests above leave
    uncovered: deleting it makes the flag a silent no-op.
    """
    from fastapi.testclient import TestClient

    captured = {}

    def fake_create_converter(**kwargs):
        captured.update(kwargs)
        return type(
            "_Converter", (), {"initialize_pipeline": lambda self, fmt: None}
        )()

    monkeypatch.setattr(hybrid_server, "create_converter", fake_create_converter)
    with TestClient(hybrid_server.create_app(document_timeout=90.0)):
        pass
    assert captured["document_timeout"] == 90.0


def test_pages_docling_blamed_land_in_failed_pages():
    """docling names a timed-out page in `ErrorItem.page_no` and leaves the
    page's entry in `pages`, so message parsing and gap detection both come
    back empty and only this path reports the loss.
    """
    response = hybrid_server.build_conversion_response(
        status_value="partial_success",
        json_content={"pages": {"1": {}, "2": {}, "3": {}}},
        processing_time=1.0,
        errors=["document timeout exceeded", "document timeout exceeded"],
        requested_pages=None,
        total_pages=3,
        error_pages=[2, 3],
    )
    assert response["failed_pages"] == [2, 3]


def test_attributed_pages_merge_with_the_other_strategies():
    response = hybrid_server.build_conversion_response(
        status_value="partial_success",
        json_content={"pages": {"1": {}, "3": {}}},
        processing_time=1.0,
        errors=["Page 1: std::bad_alloc"],
        requested_pages=None,
        total_pages=3,
        error_pages=[3],
    )
    assert response["failed_pages"] == [1, 2, 3]


def _post_pdf_returning_partial_success(
    monkeypatch, *, timed_out, error_pages=(), **app_kwargs
):
    from docling.datamodel.base_models import ConversionStatus
    from fastapi.testclient import TestClient

    errors = [
        type("_E", (), {"error_message": "document timeout exceeded", "page_no": page})()
        for page in error_pages
    ] or [
        type("_E", (), {"error_message": "Invalid code point", "page_no": None})()
    ]

    class _Result:
        status = ConversionStatus.PARTIAL_SUCCESS
        input = type("_Input", (), {"page_count": 3})()
        document = type(
            "_Doc",
            (),
            {"export_to_dict": lambda self: {"pages": {"1": {}, "2": {}, "3": {}}}},
        )()

        def __init__(self):
            self.errors = errors

        def has_timeout_errors(self):
            return timed_out

    class _Converter:
        def convert(self, path, **kwargs):
            return _Result()

    app = hybrid_server.create_app(**app_kwargs)
    monkeypatch.setattr(hybrid_server, "converter", _Converter())
    monkeypatch.setattr(hybrid_server, "_probe_page_count", lambda path: 3)

    # Not entered as a context manager: the lifespan would load real models.
    return TestClient(app).post(
        "/v1/convert/file",
        files={"files": ("x.pdf", b"%PDF-1.4\n", "application/pdf")},
    )


def test_timeout_is_named_as_the_cause(monkeypatch, caplog):
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        response = _post_pdf_returning_partial_success(
            monkeypatch, timed_out=True, error_pages=(2, 3), document_timeout=90.0
        )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "partial_success"
    assert body["failed_pages"] == [2, 3]
    assert "cause=timeout" in caplog.text
    assert "timeout=90.0s" in caplog.text
    assert "pages_failed=2" in caplog.text


def test_other_partial_successes_are_unchanged(monkeypatch, caplog):
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        response = _post_pdf_returning_partial_success(monkeypatch, timed_out=False)
    assert response.status_code == 200
    assert "partial_success" in caplog.text
    assert "cause=timeout" not in caplog.text
