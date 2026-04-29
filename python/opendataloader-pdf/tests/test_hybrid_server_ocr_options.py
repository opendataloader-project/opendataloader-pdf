"""Tests for the OCR option surface added to the hybrid server.

Covers:
    * `create_converter` delegation to docling's `get_ocr_factory`
    * Defaults preserve prior behavior (do_ocr=True, engine=easyocr)
    * `disable_ocr=True` -> do_ocr=False (#387)
    * Engine selection produces the matching OcrOptions subclass (#436, #439)
    * `psm` is applied only for Tesseract engines (silently ignored otherwise)
    * Unknown / denylisted engines exit with a clear message
    * argparse `--no-ocr` and `--force-ocr` are mutually exclusive
    * argparse `--ocr-engine` choices exclude `kserve_v2_ocr`
"""

import argparse
import io
from contextlib import redirect_stderr
from unittest.mock import patch

import pytest

from opendataloader_pdf import hybrid_server


# ---------- create_converter: factory delegation ----------

def _capture_pipeline_options(**kwargs):
    """Call create_converter while mocking the docling document_converter so we
    can introspect the PdfPipelineOptions instance that would be passed in.
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


def test_defaults_preserve_prior_behavior():
    """No flags -> do_ocr=True, EasyOcrOptions (matches pre-change behavior)."""
    from docling.datamodel.pipeline_options import EasyOcrOptions

    opts = _capture_pipeline_options()
    assert opts.do_ocr is True
    assert isinstance(opts.ocr_options, EasyOcrOptions)


def test_disable_ocr_sets_do_ocr_false():
    """`disable_ocr=True` -> do_ocr=False (#387)."""
    opts = _capture_pipeline_options(disable_ocr=True)
    assert opts.do_ocr is False


def test_ocr_engine_tesseract_yields_tesseract_options():
    """`ocr_engine='tesseract'` -> TesseractCliOcrOptions (#439 path)."""
    from docling.datamodel.pipeline_options import TesseractCliOcrOptions

    opts = _capture_pipeline_options(ocr_engine="tesseract")
    assert isinstance(opts.ocr_options, TesseractCliOcrOptions)


def test_ocr_engine_rapidocr_yields_rapidocr_options():
    """`ocr_engine='rapidocr'` -> RapidOcrOptions (#436 path)."""
    from docling.datamodel.pipeline_options import RapidOcrOptions

    opts = _capture_pipeline_options(ocr_engine="rapidocr")
    assert isinstance(opts.ocr_options, RapidOcrOptions)


def test_force_full_page_ocr_propagates_to_engine_options():
    """`force_full_page_ocr=True` flows to the engine's options instance."""
    opts = _capture_pipeline_options(
        ocr_engine="rapidocr", force_full_page_ocr=True
    )
    assert opts.ocr_options.force_full_page_ocr is True


def test_ocr_lang_overrides_engine_default():
    """A non-empty ocr_lang list replaces the engine's default lang."""
    opts = _capture_pipeline_options(
        ocr_engine="tesseract", ocr_lang=["mal", "eng"]
    )
    assert opts.ocr_options.lang == ["mal", "eng"]


def test_psm_is_applied_to_tesseract():
    """`psm` is applied when the engine is Tesseract."""
    opts = _capture_pipeline_options(ocr_engine="tesseract", psm=6)
    assert opts.ocr_options.psm == 6


def test_psm_is_ignored_for_non_tesseract_engines():
    """`psm` is silently ignored for engines that do not expose it."""
    # EasyOcrOptions has no `psm` field; passing psm should not raise.
    opts = _capture_pipeline_options(ocr_engine="easyocr", psm=6)
    assert not hasattr(opts.ocr_options, "psm")


def test_ocr_engine_auto_yields_auto_options():
    """`ocr_engine='auto'` -> OcrAutoOptions; engine choice is deferred to docling."""
    from docling.datamodel.pipeline_options import OcrAutoOptions

    opts = _capture_pipeline_options(ocr_engine="auto")
    assert isinstance(opts.ocr_options, OcrAutoOptions)


def test_unknown_engine_raises_value_error_with_clear_message():
    """An unknown engine kind raises ValueError listing valid engines.

    ValueError (not SystemExit) keeps the function library-friendly: programmatic
    callers can catch and retry with a different engine. main() relies on
    argparse `choices` to gate invalid CLI input separately.
    """
    with pytest.raises(ValueError) as excinfo:
        _capture_pipeline_options(ocr_engine="bogus-engine")
    msg = str(excinfo.value)
    assert "bogus-engine" in msg
    assert "Available engines" in msg


def test_create_converter_rejects_denylisted_engine():
    """Programmatic callers cannot bypass `_OCR_ENGINE_DENYLIST` via create_converter().

    The CLI layer rejects denylisted engines via argparse `choices`. This test
    locks in the same enforcement at the function-call layer, so importing the
    module and passing `ocr_engine="kserve_v2_ocr"` directly fails with a clear
    ValueError instead of building a converter that fails opaquely on the first
    request.
    """
    with pytest.raises(ValueError) as excinfo:
        _capture_pipeline_options(ocr_engine="kserve_v2_ocr")
    msg = str(excinfo.value)
    assert "kserve_v2_ocr" in msg
    assert "hybrid local mode" in msg
    assert "Available engines" in msg
    # Must also mention the denylist explicitly so a future maintainer searching
    # for the constant lands here.
    assert "_OCR_ENGINE_DENYLIST" in msg


# ---------- argparse: --no-ocr / --force-ocr / --ocr-engine / --psm ----------

def _build_parser_subset():
    """Reconstruct the OCR-relevant argparse subset from main().

    Mirrors the layout in hybrid_server.main() so changes to the CLI surface
    are caught by these tests. Uses the same `_OCR_ENGINE_DENYLIST` module
    constant as production code so tests stay in sync with main() automatically.
    """
    from docling.models.factories import get_ocr_factory

    parser = argparse.ArgumentParser()
    ocr_mode = parser.add_mutually_exclusive_group()
    ocr_mode.add_argument("--force-ocr", action="store_true")
    ocr_mode.add_argument("--no-ocr", action="store_true")

    choices = sorted(
        set(get_ocr_factory(allow_external_plugins=False).registered_kind)
        - hybrid_server._OCR_ENGINE_DENYLIST
    )
    parser.add_argument(
        "--ocr-engine", default="easyocr", choices=choices
    )
    parser.add_argument("--psm", type=int, default=None)
    parser.add_argument("--ocr-lang", default=None)
    return parser


def test_argparse_defaults():
    """No flags -> no_ocr=False, force_ocr=False, engine=easyocr, psm=None."""
    args = _build_parser_subset().parse_args([])
    assert args.no_ocr is False
    assert args.force_ocr is False
    assert args.ocr_engine == "easyocr"
    assert args.psm is None


def test_argparse_no_ocr_and_force_ocr_are_mutually_exclusive():
    """Passing both --no-ocr and --force-ocr exits with an argparse error."""
    err = io.StringIO()
    with pytest.raises(SystemExit), redirect_stderr(err):
        _build_parser_subset().parse_args(["--no-ocr", "--force-ocr"])
    assert "not allowed with argument" in err.getvalue()


def test_argparse_kserve_engine_is_rejected():
    """`--ocr-engine kserve_v2_ocr` is rejected (denylist filter)."""
    err = io.StringIO()
    with pytest.raises(SystemExit), redirect_stderr(err):
        _build_parser_subset().parse_args(["--ocr-engine", "kserve_v2_ocr"])
    assert "invalid choice" in err.getvalue()


def test_argparse_tesseract_path_for_issue_439():
    """The CLI invocation that resolves #439 parses cleanly."""
    args = _build_parser_subset().parse_args(
        ["--ocr-engine", "tesseract", "--ocr-lang", "mal", "--force-ocr"]
    )
    assert args.ocr_engine == "tesseract"
    assert args.ocr_lang == "mal"
    assert args.force_ocr is True
    assert args.no_ocr is False


def test_argparse_psm_accepted_as_integer():
    """`--psm 6` is parsed as an integer.

    Range and semantics belong to Tesseract / docling; this server passes the
    integer through unchanged. Out-of-range values surface from docling /
    Tesseract at conversion time, not here.
    """
    args = _build_parser_subset().parse_args(
        ["--ocr-engine", "tesseract", "--psm", "6"]
    )
    assert args.psm == 6


# ---------- _check_ocr_engine_available ----------

def test_engine_check_easyocr_always_ok():
    """easyocr ships with the `[hybrid]` extra; check is a no-op."""
    ok, msg = hybrid_server._check_ocr_engine_available("easyocr")
    assert ok is True
    assert msg == ""


def test_engine_check_auto_always_ok():
    """`auto` defers engine choice to docling; check is a no-op."""
    ok, msg = hybrid_server._check_ocr_engine_available("auto")
    assert ok is True
    assert msg == ""


def test_engine_check_unknown_kind_returns_false():
    """Unknown engine kinds fail closed instead of silently passing.

    Before this contract was tightened, the helper returned `(True, "")` for any
    engine name it did not recognize, so a docling release that registered a
    new engine kind would slip past the probe and fail at first conversion.
    Locks in the fail-closed behavior.
    """
    ok, msg = hybrid_server._check_ocr_engine_available("hypothetical_new_engine")
    assert ok is False
    assert "hypothetical_new_engine" in msg
    # Maintainer-targeted hint: where to add the probe branch.
    assert "_check_ocr_engine_available" in msg


def test_engine_check_tesseract_missing_binary():
    """`tesseract` engine without the binary on PATH fails with an actionable message."""
    with patch("shutil.which", return_value=None):
        ok, msg = hybrid_server._check_ocr_engine_available("tesseract")
    assert ok is False
    assert "tesseract" in msg.lower()
    assert "PATH" in msg


def test_engine_check_tesseract_present():
    """`tesseract` engine passes when the binary resolves on PATH."""
    with patch("shutil.which", return_value="/usr/bin/tesseract"):
        ok, msg = hybrid_server._check_ocr_engine_available("tesseract")
    assert ok is True
    assert msg == ""


def test_engine_check_tesserocr_missing_package():
    """`tesserocr` engine without the Python package fails with install hint."""
    with patch("importlib.util.find_spec", return_value=None):
        ok, msg = hybrid_server._check_ocr_engine_available("tesserocr")
    assert ok is False
    assert "tesserocr" in msg
    assert "pip install" in msg


def test_engine_check_rapidocr_missing_package():
    """`rapidocr` engine without the Python package fails with install hint."""
    with patch("importlib.util.find_spec", return_value=None):
        ok, msg = hybrid_server._check_ocr_engine_available("rapidocr")
    assert ok is False
    assert "rapidocr" in msg
    assert "onnxruntime" in msg


def test_engine_check_rapidocr_missing_onnxruntime():
    """`rapidocr` installed without `onnxruntime` fails with a backend-specific hint."""
    # Return a truthy spec for rapidocr, None for onnxruntime, None for anything else.
    def fake_find_spec(name):
        return object() if name == "rapidocr" else None

    with patch("importlib.util.find_spec", side_effect=fake_find_spec):
        ok, msg = hybrid_server._check_ocr_engine_available("rapidocr")
    assert ok is False
    assert "onnxruntime" in msg
    # The message should not duplicate the "rapidocr is not installed" wording.
    assert "rapidocr` Python package" not in msg


def test_engine_check_ocrmac_off_macos():
    """`ocrmac` selected on a non-Darwin platform fails with a clear platform message."""
    with patch("sys.platform", "linux"):
        ok, msg = hybrid_server._check_ocr_engine_available("ocrmac")
    assert ok is False
    assert "macOS" in msg


def test_engine_check_ocrmac_on_macos_missing_package():
    """`ocrmac` on macOS without the `ocrmac` Python package fails with install hint."""
    with patch("sys.platform", "darwin"), patch(
        "importlib.util.find_spec", return_value=None
    ):
        ok, msg = hybrid_server._check_ocr_engine_available("ocrmac")
    assert ok is False
    assert "ocrmac" in msg
    assert "pip install" in msg
    # Should NOT be the platform-mismatch message.
    assert "macOS only" not in msg and "not macOS" not in msg


# ---------- --no-ocr ignored-flag warning ----------

def _run_main_to_warning(argv, monkeypatch, caplog):
    """Drive `main()` far enough to capture the --no-ocr warning, then short-circuit.

    `main()` ends in `uvicorn.run(...)`; we patch `create_app` and `uvicorn.run`
    so the function returns cleanly after argparse + the warning logic without
    starting a server.
    """
    monkeypatch.setattr("sys.argv", ["opendataloader-pdf-hybrid", *argv])

    # Skip dep import checks — pytest already runs inside the [hybrid] env.
    monkeypatch.setattr(hybrid_server, "_check_dependencies", lambda: None)

    # Avoid building a real DocumentConverter / FastAPI app.
    monkeypatch.setattr(hybrid_server, "create_app", lambda **kwargs: object())

    # Stub uvicorn so main() returns instead of starting a server.
    fake_uvicorn = type("FakeUvicorn", (), {"run": staticmethod(lambda *a, **k: None)})
    monkeypatch.setitem(__import__("sys").modules, "uvicorn", fake_uvicorn)

    caplog.set_level("WARNING", logger=hybrid_server.logger.name)
    hybrid_server.main()


def test_no_ocr_warns_when_engine_explicitly_set(monkeypatch, caplog):
    """`--no-ocr --ocr-engine tesseract` warns that --ocr-engine has no effect."""
    _run_main_to_warning(
        ["--no-ocr", "--ocr-engine", "tesseract"], monkeypatch, caplog
    )
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    assert any("--ocr-engine tesseract" in w for w in warnings), warnings


def test_no_ocr_warns_when_engine_explicitly_set_to_easyocr(monkeypatch, caplog):
    """Explicit `--ocr-engine easyocr` under --no-ocr is still inert and must warn.

    argparse cannot distinguish "user typed easyocr" from "default was used", so
    main() inspects argv directly. This regression test locks the path in.
    """
    _run_main_to_warning(
        ["--no-ocr", "--ocr-engine", "easyocr"], monkeypatch, caplog
    )
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    assert any("--ocr-engine easyocr" in w for w in warnings), warnings


def test_no_ocr_no_warning_when_engine_left_default(monkeypatch, caplog):
    """`--no-ocr` without --ocr-engine does not falsely report easyocr as inert."""
    _run_main_to_warning(["--no-ocr"], monkeypatch, caplog)
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    # No "--ocr-engine" mention because the user did not type it.
    assert not any("--ocr-engine" in w for w in warnings), warnings


def test_no_ocr_warns_when_ocr_lang_set(monkeypatch, caplog):
    """`--no-ocr --ocr-lang ko` warns that --ocr-lang has no effect."""
    _run_main_to_warning(["--no-ocr", "--ocr-lang", "ko"], monkeypatch, caplog)
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    assert any("--ocr-lang" in w for w in warnings), warnings


def test_no_ocr_warns_when_psm_set(monkeypatch, caplog):
    """`--no-ocr --psm 6` warns that --psm has no effect."""
    _run_main_to_warning(["--no-ocr", "--psm", "6"], monkeypatch, caplog)
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    assert any("--psm 6" in w for w in warnings), warnings


def test_no_ocr_alone_emits_no_warning(monkeypatch, caplog):
    """`--no-ocr` on its own does not warn — there are no inert flags to call out."""
    _run_main_to_warning(["--no-ocr"], monkeypatch, caplog)
    warnings = [
        r.message
        for r in caplog.records
        if r.levelname == "WARNING" and "no effect" in r.message
    ]
    assert warnings == []


# ---------- main() exits when engine prerequisites are missing ----------

def test_main_exits_when_tesseract_binary_missing(monkeypatch, caplog):
    """Selecting `--ocr-engine tesseract` without the binary on PATH exits at startup."""
    monkeypatch.setattr(
        "sys.argv", ["opendataloader-pdf-hybrid", "--ocr-engine", "tesseract"]
    )
    monkeypatch.setattr(hybrid_server, "_check_dependencies", lambda: None)
    monkeypatch.setattr(hybrid_server, "create_app", lambda **kwargs: object())
    monkeypatch.setattr("shutil.which", lambda _name: None)
    fake_uvicorn = type("FakeUvicorn", (), {"run": staticmethod(lambda *a, **k: None)})
    monkeypatch.setitem(__import__("sys").modules, "uvicorn", fake_uvicorn)

    caplog.set_level("ERROR", logger=hybrid_server.logger.name)
    with pytest.raises(SystemExit) as excinfo:
        hybrid_server.main()
    assert excinfo.value.code == 2
    errors = [r.message for r in caplog.records if r.levelname == "ERROR"]
    assert any("tesseract" in e.lower() for e in errors), errors


def test_main_skips_engine_check_when_no_ocr(monkeypatch, caplog):
    """`--no-ocr --ocr-engine tesseract` skips the binary check entirely."""
    called = {"which": False}

    def spy_which(_name):
        called["which"] = True
        return None  # Would fail the check if it were called.

    monkeypatch.setattr(
        "sys.argv",
        ["opendataloader-pdf-hybrid", "--no-ocr", "--ocr-engine", "tesseract"],
    )
    monkeypatch.setattr(hybrid_server, "_check_dependencies", lambda: None)
    monkeypatch.setattr(hybrid_server, "create_app", lambda **kwargs: object())
    monkeypatch.setattr("shutil.which", spy_which)
    fake_uvicorn = type("FakeUvicorn", (), {"run": staticmethod(lambda *a, **k: None)})
    monkeypatch.setitem(__import__("sys").modules, "uvicorn", fake_uvicorn)

    hybrid_server.main()  # must not raise SystemExit
    assert called["which"] is False
