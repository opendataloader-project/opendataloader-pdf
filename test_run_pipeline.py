"""Regression checks for the turnkey runner's output boundary."""

import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

import run_pipeline


def test_empty_run_does_not_report_stale_outputs(tmp_path, monkeypatch):
    output = tmp_path / "output"
    output.mkdir()
    (output / "old.md").write_text("previous", encoding="utf-8")
    (output / "old.json").write_text("{}", encoding="utf-8")
    monkeypatch.setitem(sys.modules, "opendataloader_pdf", SimpleNamespace(convert=lambda **_: None))

    with pytest.raises(RuntimeError, match="produced no Markdown/JSON output"):
        run_pipeline.run_conversion(str(tmp_path / "empty"), str(output))

    assert (output / "old.md").read_text(encoding="utf-8") == "previous"
    assert not list(output.glob(".conversion-*"))



def test_partial_run_does_not_promote_artifacts(tmp_path, monkeypatch):
    output = tmp_path / "output"
    output.mkdir()
    (output / "paper.md").write_text("previous", encoding="utf-8")

    def convert(**kwargs):
        (Path(kwargs["output_dir"]) / "paper.md").write_text("partial", encoding="utf-8")

    monkeypatch.setitem(sys.modules, "opendataloader_pdf", SimpleNamespace(convert=convert))
    with pytest.raises(RuntimeError, match="produced no Markdown/JSON output"):
        run_pipeline.run_conversion(str(tmp_path / "paper.pdf"), str(output))

    assert (output / "paper.md").read_text(encoding="utf-8") == "previous"
    assert not list(output.glob(".conversion-*"))


def test_repeated_run_reports_current_artifacts(tmp_path, monkeypatch, capsys):
    output = tmp_path / "output"
    output.mkdir()
    (output / "paper.md").write_text("previous", encoding="utf-8")
    (output / "paper.json").write_text("{}", encoding="utf-8")

    def convert(**kwargs):
        destination = Path(kwargs["output_dir"])
        (destination / "paper.md").write_text("current", encoding="utf-8")
        (destination / "paper.json").write_text(json.dumps({"number of pages": 1}), encoding="utf-8")

    monkeypatch.setitem(sys.modules, "opendataloader_pdf", SimpleNamespace(convert=convert))
    run_pipeline.run_conversion(str(tmp_path / "paper.pdf"), str(output))

    assert (output / "paper.md").read_text(encoding="utf-8") == "current"
    assert "paper.md" in capsys.readouterr().out
    assert not list(output.glob(".conversion-*"))
