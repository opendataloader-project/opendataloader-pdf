from pathlib import Path
from subprocess import CompletedProcess

import opendataloader_pdf.wrapper as wrapper
from opendataloader_pdf.markdown_fallback import repair_markdown_outputs


COLLAPSED_MARKDOWN = """|CONTO ECONOMICO|2015 2016 2017 2018|2015 2016 2017 2018|2015 2016 2017 2018|2015 2016 2017 2018|
|---|---|---|---|---|
|A) VALORE DELLA PRODUZIONE<br><br>1) Ricavi delle vendite e delle prestazioni<br>2) Variazione rimanenze prodotti in corso di lavor., semilavorati e finiti<br>3) Variazione dei lavori in corso su ordinazione<br>4) Incrementi di immobilizzazioni per lavori interni<br>5) Altri ricavi e proventi<br>Vari<br>Contributo in conto esercizio<br>B) COSTI DELLA PRODUZIONE|124.504.000 120.062.000 1.942.000 0 0 2.500.000 2.500.000 0|127.608.000 124.406.000 117.000 0 0 3.085.000 3.085.000 0|120.915.000 115.322.000 2.538.000 0 0 3.055.000 3.055.000 0|80.579.000 82.776.000 -3.970.000 0 0 1.773.000 1.773.000 0|
"""

LAYOUT_TEXT = """                              CONTO ECONOMICO                                       2015          2016          2017               2018
A) VALORE DELLA PRODUZIONE                                                       124.504.000   127.608.000   120.915.000       80.579.000
 1) Ricavi delle vendite e delle prestazioni                                     120.062.000   124.406.000   115.322.000       82.776.000
"""


def test_repair_markdown_outputs_rebuilds_collapsed_table(tmp_path, monkeypatch):
    pdf_path = tmp_path / "conto.pdf"
    pdf_path.write_bytes(b"%PDF-1.4")

    markdown_path = tmp_path / "conto.md"
    markdown_path.write_text(COLLAPSED_MARKDOWN, encoding="utf-8")

    def fake_run(*args, **kwargs):
        return CompletedProcess(args=args[0], returncode=0, stdout=LAYOUT_TEXT)

    monkeypatch.setattr("opendataloader_pdf.markdown_fallback.subprocess.run", fake_run)

    repair_markdown_outputs(str(pdf_path), str(tmp_path), "markdown")

    content = markdown_path.read_text(encoding="utf-8")
    assert "# CONTO ECONOMICO" in content
    assert "| A) VALORE DELLA PRODUZIONE | 124.504.000 | 127.608.000 | 120.915.000 | 80.579.000 |" in content
    assert "| &nbsp;1) Ricavi delle vendite e delle prestazioni | 120.062.000 | 124.406.000 | 115.322.000 | 82.776.000 |" in content


def test_repair_markdown_outputs_skips_non_collapsed_markdown(tmp_path, monkeypatch):
    pdf_path = tmp_path / "conto.pdf"
    pdf_path.write_bytes(b"%PDF-1.4")

    markdown_path = tmp_path / "conto.md"
    markdown_path.write_text("# already fine\n", encoding="utf-8")

    def fail_run(*args, **kwargs):
        raise AssertionError("pdftotext should not run for clean markdown")

    monkeypatch.setattr("opendataloader_pdf.markdown_fallback.subprocess.run", fail_run)

    repair_markdown_outputs(str(pdf_path), str(tmp_path), "markdown")

    assert markdown_path.read_text(encoding="utf-8") == "# already fine\n"


def test_wrapper_convert_calls_fallback(monkeypatch):
    calls = []

    def fake_convert(*args, **kwargs):
        calls.append(("convert", args, kwargs))

    def fake_repair(*args, **kwargs):
        calls.append(("repair", args, kwargs))

    monkeypatch.setattr(wrapper, "_convert_generated", fake_convert)
    monkeypatch.setattr(wrapper, "repair_markdown_outputs", fake_repair)

    wrapper.convert(input_path="sample.pdf", output_dir="out", format="markdown", quiet=True)

    assert calls == [
        ("convert", (), {"input_path": "sample.pdf", "output_dir": "out", "format": "markdown", "quiet": True}),
        ("repair", (), {"input_path": "sample.pdf", "output_dir": "out", "format_value": "markdown"}),
    ]
