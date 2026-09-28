"""Tests for the hybrid server's picture-description options and tally.

Covers two reports of a flag that accepted input and changed nothing:
PDFDLOSP-20 (`--picture-description-prompt` never reached the VLM) and #418
(every picture under 5% of its page went undescribed, with nothing in the log
to say so). These tests lock in the contract:

    * A custom prompt reaches `PictureDescriptionVlmOptions.prompt`.
    * Omitting the prompt preserves docling's built-in default.
    * Blank / whitespace-only prompts are treated as "not provided" so they
      never silently inject an empty prompt into the VLM.
    * The area threshold defaults to 0 -- no limit -- and a custom value
      reaches the VLM options.
    * The CLI defaults arrive at `create_app`, and a modifier given without
      the enrichment is reported rather than dropped.
    * The per-request tally reaches the log, and cannot fail the response.
    * The whole feature is gated by `enrich_picture_description=True`.
"""

import io
import sys
from contextlib import redirect_stderr
from unittest.mock import patch

import pytest

from opendataloader_pdf import hybrid_server


def _capture_pipeline_options(**kwargs):
    """Build a converter while mocking out docling's heavy bits so we can
    introspect the PdfPipelineOptions instance.
    """
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


def test_custom_prompt_is_forwarded_to_vlm_options():
    """The user-supplied prompt must end up on PictureDescriptionVlmOptions.

    Regression for PDFDLOSP-20.
    """
    opts = _capture_pipeline_options(
        enrich_picture_description=True,
        picture_description_prompt="HELLO WORLD",
    )
    assert opts.picture_description_options is not None
    assert opts.picture_description_options.prompt == "HELLO WORLD"


def test_default_prompt_is_preserved_when_user_omits_flag():
    """When no prompt is given, docling's built-in default must survive.

    Locks the current docling default. If a docling upgrade changes the
    phrase, this canary fires — at which point we decide whether to track
    the new default or pin our own.
    """
    opts = _capture_pipeline_options(enrich_picture_description=True)
    assert opts.picture_description_options is not None
    assert (
        opts.picture_description_options.prompt
        == "Describe this image in a few sentences."
    )


@pytest.mark.parametrize("blank", ["", "   ", "\t\n"])
def test_blank_prompt_falls_back_to_default(blank):
    """Empty / whitespace-only prompts must not inject an empty prompt.

    Otherwise we recreate the same class of silent-flag misbehavior that
    PDFDLOSP-20 reported.
    """
    opts = _capture_pipeline_options(
        enrich_picture_description=True,
        picture_description_prompt=blank,
    )
    assert opts.picture_description_options is not None
    assert (
        opts.picture_description_options.prompt
        == "Describe this image in a few sentences."
    )


def test_prompt_ignored_when_enrichment_disabled():
    """Without --enrich-picture-description, the prompt has no destination.

    Docling populates a default `picture_description_options` object on
    `PdfPipelineOptions` even when do_picture_description=False, so we
    assert on the gate (`do_picture_description`) rather than identity of
    the options object.
    """
    opts = _capture_pipeline_options(
        enrich_picture_description=False,
        picture_description_prompt="HELLO WORLD",
    )
    assert opts.do_picture_description is False


def test_area_threshold_defaults_to_no_limit():
    """Locks the decision to override docling's 0.05 with 0 (#418)."""
    opts = _capture_pipeline_options(enrich_picture_description=True)
    assert opts.picture_description_options is not None
    assert opts.picture_description_options.picture_area_threshold == 0.0


def test_custom_area_threshold_is_forwarded_to_vlm_options():
    """A user-supplied threshold must reach PictureDescriptionVlmOptions.

    The value deliberately differs from docling's own default, so dropping the
    assignment fails this test instead of passing on the default.
    """
    opts = _capture_pipeline_options(
        enrich_picture_description=True,
        picture_area_threshold=0.2,
    )
    assert opts.picture_description_options is not None
    assert opts.picture_description_options.picture_area_threshold == 0.2


def test_area_threshold_ignored_when_enrichment_disabled():
    """Docling populates a default options object even when the gate is off,
    so assert on the gate.
    """
    opts = _capture_pipeline_options(
        enrich_picture_description=False,
        picture_area_threshold=0.5,
    )
    assert opts.do_picture_description is False


def _build_parser_subset():
    """Reconstruct the argparse subset that `--picture-area-threshold` needs.

    Shares `hybrid_server._area_fraction`, so the range check here exercises
    the production validator. The default is declared again below and does not
    track `main()`; `test_cli_defaults_reach_create_app` covers that instead.
    """
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--picture-area-threshold",
        type=hybrid_server._area_fraction,
        default=0.0,
    )
    return parser


def test_argparse_area_threshold_accepts_a_fraction():
    args = _build_parser_subset().parse_args(["--picture-area-threshold", "0.05"])
    assert args.picture_area_threshold == 0.05


@pytest.mark.parametrize("bad", ["-0.1", "1.5"])
def test_argparse_area_threshold_rejects_values_outside_the_unit_range(bad):
    """The value is a fraction of the page area, so only 0..1 can mean anything."""
    err = io.StringIO()
    with pytest.raises(SystemExit), redirect_stderr(err):
        _build_parser_subset().parse_args(["--picture-area-threshold", bad])
    assert "between 0 and 1" in err.getvalue()


def _run_main(argv, monkeypatch):
    """Drive `main()` through argparse and return the `create_app` kwargs.

    `main()` ends in `uvicorn.run(...)`; patching `create_app` and uvicorn lets
    it return after the flag handling without starting a server, and hands back
    what the CLI actually resolved.
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


def test_cli_defaults_reach_create_app(monkeypatch):
    """Reintroducing docling's 0.05 as the CLI default must fail here."""
    kwargs = _run_main(["--enrich-picture-description"], monkeypatch)
    assert kwargs["picture_area_threshold"] == 0.0


def test_cli_threshold_reaches_create_app(monkeypatch):
    kwargs = _run_main(
        ["--enrich-picture-description", "--picture-area-threshold", "0.05"],
        monkeypatch,
    )
    assert kwargs["picture_area_threshold"] == 0.05


def test_threshold_without_the_enrichment_is_reported(monkeypatch, caplog):
    """A modifier that cannot modify anything is named, not dropped."""
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        _run_main(["--picture-area-threshold", "0.2"], monkeypatch)
    assert "inert_flags" in caplog.text
    assert "--picture-area-threshold 0.2" in caplog.text


def test_prompt_without_the_enrichment_is_reported(monkeypatch, caplog):
    """The prompt is named without its value, which is the operator's text."""
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        _run_main(["--picture-description-prompt", "describe this"], monkeypatch)
    assert "--picture-description-prompt" in caplog.text
    assert "describe this" not in caplog.text


def test_no_inert_warning_when_the_enrichment_is_on(monkeypatch, caplog):
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        _run_main(
            ["--enrich-picture-description", "--picture-area-threshold", "0.2"],
            monkeypatch,
        )
    assert "inert_flags" not in caplog.text


def _picture(description=None, in_meta=False):
    picture = {"annotations": []}
    if description is not None and not in_meta:
        picture["annotations"].append({"kind": "description", "text": description})
    if description is not None and in_meta:
        picture["meta"] = {"description": {"text": description}}
    return picture


def test_counts_pictures_with_and_without_a_description():
    json_content = {
        "pictures": [_picture("a chart"), _picture(), _picture("a photo")]
    }
    assert hybrid_server._picture_description_counts(json_content) == (3, 2)


def test_counts_a_description_stored_only_in_meta():
    """docling is migrating the text from `annotations` to `meta.description`,
    so the tally reads both.
    """
    json_content = {"pictures": [_picture("a chart", in_meta=True)]}
    assert hybrid_server._picture_description_counts(json_content) == (1, 1)


def test_counts_an_empty_description_as_missing():
    json_content = {"pictures": [_picture("")]}
    assert hybrid_server._picture_description_counts(json_content) == (1, 0)


def test_counts_a_document_without_pictures():
    assert hybrid_server._picture_description_counts({}) == (0, 0)


def test_unexplained_skips_warn(caplog):
    """No threshold was asked for, so a picture without a description is an
    anomaly worth a warning -- the state #418 had no signal for.
    """
    json_content = {"pictures": [_picture("a chart"), _picture(), _picture()]}
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        hybrid_server._log_picture_descriptions(json_content, picture_area_threshold=0.0)
    assert "pictures=3" in caplog.text
    assert "described=1" in caplog.text
    assert "skipped=2" in caplog.text
    assert "threshold" not in caplog.text
    assert [r.levelname for r in caplog.records] == ["WARNING"]


def test_requested_skips_stay_at_info_and_name_the_threshold(caplog):
    """An operator who set a threshold asked for these skips; warning on every
    request would fire an alert at their own configuration.
    """
    json_content = {"pictures": [_picture("a chart"), _picture()]}
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        hybrid_server._log_picture_descriptions(json_content, picture_area_threshold=0.05)
    assert "skipped=1" in caplog.text
    assert "threshold=0.05" in caplog.text
    assert [r.levelname for r in caplog.records] == ["INFO"]


def test_all_described_logs_at_info(caplog):
    json_content = {"pictures": [_picture("a chart")]}
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        hybrid_server._log_picture_descriptions(json_content, picture_area_threshold=0.0)
    assert "described=1" in caplog.text
    assert "skipped" not in caplog.text
    assert [r.levelname for r in caplog.records] == ["INFO"]


def test_a_document_without_pictures_logs_nothing(caplog):
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        hybrid_server._log_picture_descriptions({}, picture_area_threshold=0.0)
    assert caplog.records == []


def _post_pdf(monkeypatch, json_content, **app_kwargs):
    """POST one PDF through a real app with the converter replaced.

    Exercises the wiring in `convert_file`, which unit tests on the helpers
    cannot reach: deleting the call there leaves them all passing.
    """
    from docling.datamodel.base_models import ConversionStatus
    from fastapi.testclient import TestClient

    class _Result:
        status = ConversionStatus.SUCCESS
        errors = []
        input = type("_Input", (), {"page_count": 1})()
        document = type(
            "_Doc", (), {"export_to_dict": lambda self: json_content}
        )()

    class _Converter:
        def convert(self, path, **kwargs):
            return _Result()

    app = hybrid_server.create_app(**app_kwargs)
    monkeypatch.setattr(hybrid_server, "converter", _Converter())
    monkeypatch.setattr(hybrid_server, "_probe_page_count", lambda path: 1)

    # Not entered as a context manager: the lifespan would load real models.
    return TestClient(app).post(
        "/v1/convert/file",
        files={"files": ("x.pdf", b"%PDF-1.4\n", "application/pdf")},
    )


def test_convert_endpoint_logs_the_tally(monkeypatch, caplog):
    json_content = {"pictures": [_picture("a chart"), _picture()], "pages": {"1": {}}}
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        response = _post_pdf(
            monkeypatch, json_content, enrich_picture_description=True
        )
    assert response.status_code == 200
    assert "picture_description pictures=2 described=1" in caplog.text


def test_convert_endpoint_stays_quiet_without_the_enrichment(monkeypatch, caplog):
    json_content = {"pictures": [_picture(), _picture()], "pages": {"1": {}}}
    with caplog.at_level("INFO", logger=hybrid_server.logger.name):
        response = _post_pdf(monkeypatch, json_content)
    assert response.status_code == 200
    assert "picture_description" not in caplog.text


def test_a_failing_tally_does_not_fail_the_conversion(monkeypatch, caplog):
    """The tally reads a shape docling owns; a change there must cost a log
    line, not a converted document.
    """
    def boom(*args, **kwargs):
        raise TypeError("shape changed")

    monkeypatch.setattr(hybrid_server, "_picture_description_counts", boom)
    json_content = {"pictures": [_picture("a chart")], "pages": {"1": {}}}
    with caplog.at_level("WARNING", logger=hybrid_server.logger.name):
        response = _post_pdf(
            monkeypatch, json_content, enrich_picture_description=True
        )
    assert response.status_code == 200
    assert "picture_description_tally_failed" in caplog.text
