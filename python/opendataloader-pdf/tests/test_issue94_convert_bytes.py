"""Issue #94: the bytes of a PDF as input of the Python package.

convert() takes only file paths, so a caller with the bytes of a PDF must write a file first.
convert_bytes() takes the bytes, writes them to a private temporary folder, runs convert() on that
file, and returns the output as a string. It removes the folder after the run, also after an error.

The expected output is not written in this file. It is the output that convert() writes for the
same PDF as a file (the cases are in verification/convert-bytes-cases.json, which the tests of the
Node package read too). The unit tests replace the JAR with a stand-in and check what
convert_bytes() gives to it.
"""

import inspect
import json
import os
import shutil
import stat
import subprocess
import tempfile
from pathlib import Path

import pytest

import opendataloader_pdf
from opendataloader_pdf import convert_generated

REPO_ROOT = Path(__file__).resolve().parents[3]
TABLE = json.loads(
    (REPO_ROOT / "verification" / "convert-bytes-cases.json").read_text(encoding="utf-8")
)
EXTENSIONS = {"markdown": "md", "text": "txt", "json": "json", "html": "html"}
SYMPTOM = "no PDF bytes input: opendataloader_pdf has no convert_bytes()"
STAND_IN_PDF = b"%PDF-1.7 stand-in"
# The options that the bytes input sets itself; the other options of convert() pass through.
SET_BY_BYTES_INPUT = {
    "input_path",
    "output_dir",
    "format",
    "image_output",
    "to_stdout",
    "image_dir",
}


def _convert_bytes():
    function = getattr(opendataloader_pdf, "convert_bytes", None)
    if function is None:
        pytest.fail(SYMPTOM)
    return function


def _python_options(cli_options):
    return {key.replace("-", "_"): value for key, value in cli_options.items()}


def _stand_in(seen, failure=None):
    """A run_jar that records its arguments and the input file, then writes the output or fails."""

    def run_jar(args, quiet=False, timeout=None, relay=False):
        pdf = Path(next(arg for arg in args if arg.endswith(".pdf")))
        out = Path(args[args.index("--output-dir") + 1])
        record = {"args": list(args)}
        if pdf.exists():
            record["bytes"] = pdf.read_bytes()
            record["file_mode"] = stat.S_IMODE(os.stat(pdf).st_mode)
            record["folder_mode"] = stat.S_IMODE(os.stat(pdf.parent).st_mode)
        seen.append(record)
        if failure is not None:
            raise failure
        extension = EXTENSIONS[args[args.index("--format") + 1]]
        out.mkdir(parents=True, exist_ok=True)
        (out / f"{pdf.stem}.{extension}").write_text("stand-in output", encoding="utf-8")
        return ""

    return run_jar


@pytest.mark.parametrize("case", TABLE["cases"], ids=[case["id"] for case in TABLE["cases"]])
def test_output_is_the_output_of_convert_for_the_same_file(case, tmp_path):
    convert_bytes = _convert_bytes()
    pdf = REPO_ROOT / case["pdf"]
    options = _python_options(case["options"])
    file_options = dict(options)
    file_options.setdefault("image_output", "off")
    named = tmp_path / "in" / f"{TABLE['name']}.pdf"
    named.parent.mkdir()
    shutil.copyfile(pdf, named)
    out = tmp_path / "out"
    opendataloader_pdf.convert(
        input_path=str(named), output_dir=str(out), quiet=True, **file_options
    )
    expected = (out / f"{TABLE['name']}.{EXTENSIONS[options['format']]}").read_text(
        encoding="utf-8"
    )

    actual = convert_bytes(pdf.read_bytes(), quiet=True, **options)

    assert actual == expected


def test_every_option_reaches_the_jar_unchanged(monkeypatch, tmp_path):
    convert_bytes = _convert_bytes()
    # Each temporary folder of this test is in tmp_path, which pytest removes.
    # The test removes nothing itself.
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))
    options = {}
    for name, parameter in inspect.signature(opendataloader_pdf.convert).parameters.items():
        if name in SET_BY_BYTES_INPUT:
            continue
        if name == "timeout":
            options[name] = 30.0
        else:
            options[name] = True if parameter.default is False else "1"

    convert_bytes(STAND_IN_PDF, format="json", **options)
    from_bytes = seen[0]["args"]
    pdf = Path(next(arg for arg in from_bytes if arg.endswith(".pdf")))
    out = from_bytes[from_bytes.index("--output-dir") + 1]
    assert (
        pdf.parent.parent == tmp_path
    ), "the temporary folder of convert_bytes() is not in tmp_path"
    pdf.parent.mkdir()
    pdf.write_bytes(STAND_IN_PDF)
    opendataloader_pdf.convert(
        input_path=str(pdf), output_dir=out, format="json", image_output="off", **options
    )

    assert seen[1]["args"] == from_bytes
    assert seen[0]["bytes"] == STAND_IN_PDF
    assert pdf.name == f"{TABLE['name']}.pdf"


def test_name_names_the_input_file(monkeypatch):
    convert_bytes = _convert_bytes()
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))

    convert_bytes(STAND_IN_PDF, format="markdown", name="report")

    assert Path(next(arg for arg in seen[0]["args"] if arg.endswith(".pdf"))).name == "report.pdf"


@pytest.mark.parametrize(
    "options, named",
    [
        ({"input_path": "a.pdf"}, "input_path"),
        ({"output_dir": "out"}, "output_dir"),
        ({"to_stdout": True}, "to_stdout"),
        ({"image_dir": "images"}, "image_dir"),
        ({"image_output": "external"}, "image_output"),
        ({"format": "pdf"}, "format"),
        ({"format": "tagged-pdf"}, "format"),
        ({"format": "json,markdown"}, "format"),
        ({"format": ["json", "markdown"]}, "format"),
        ({"name": "../document"}, "name"),
        ({"name": ""}, "name"),
    ],
    ids=lambda value: json.dumps(value) if isinstance(value, dict) else value,
)
def test_an_option_that_the_bytes_input_sets_is_refused(options, named, monkeypatch):
    convert_bytes = _convert_bytes()
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))
    options = {"format": "markdown", **options}

    with pytest.raises(ValueError) as error:
        convert_bytes(STAND_IN_PDF, **options)

    assert named in str(error.value)
    assert seen == []


@pytest.mark.parametrize(
    "data, error",
    [(b"", ValueError), (bytearray(), ValueError), ("%PDF-1.7", TypeError), (None, TypeError)],
    ids=["empty bytes", "empty bytearray", "str", "None"],
)
def test_empty_or_other_data_is_refused_before_the_jar(data, error, monkeypatch):
    convert_bytes = _convert_bytes()
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))

    with pytest.raises(error):
        convert_bytes(data, format="markdown")

    assert seen == []


def test_bytearray_and_memoryview_are_accepted(monkeypatch):
    convert_bytes = _convert_bytes()
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))

    convert_bytes(bytearray(STAND_IN_PDF), format="markdown")
    convert_bytes(memoryview(STAND_IN_PDF), format="markdown")

    assert [record["bytes"] for record in seen] == [STAND_IN_PDF, STAND_IN_PDF]


def test_a_strided_memoryview_gives_its_bytes(monkeypatch, tmp_path):
    convert_bytes = _convert_bytes()
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))
    backing = bytearray(len(STAND_IN_PDF) * 2)
    backing[::2] = STAND_IN_PDF
    view = memoryview(backing)[::2]

    convert_bytes(view, format="markdown")

    assert seen[0]["bytes"] == STAND_IN_PDF


@pytest.mark.parametrize(
    "data, options",
    [
        (b"This is not a PDF.", {"format": "markdown"}),
        (
            (REPO_ROOT / "samples" / "pdf" / "password-protected.pdf").read_bytes(),
            {"format": "markdown"},
        ),
        (
            (REPO_ROOT / "samples" / "pdf" / "password-protected.pdf").read_bytes(),
            {"format": "markdown", "password": "wrong"},
        ),
    ],
    ids=["not a PDF", "no password", "wrong password"],
)
def test_a_failure_is_the_failure_of_convert_for_the_same_file(data, options, tmp_path):
    convert_bytes = _convert_bytes()
    named = tmp_path / f"{TABLE['name']}.pdf"
    named.write_bytes(data)
    with pytest.raises(subprocess.CalledProcessError) as from_file:
        opendataloader_pdf.convert(
            input_path=str(named),
            output_dir=str(tmp_path / "out"),
            image_output="off",
            quiet=True,
            **options,
        )

    with pytest.raises(subprocess.CalledProcessError) as from_bytes:
        convert_bytes(data, quiet=True, **options)

    assert from_bytes.value.returncode == from_file.value.returncode


def test_no_output_file_is_an_error(monkeypatch):
    convert_bytes = _convert_bytes()

    def run_jar(args, quiet=False, timeout=None, relay=False):
        return ""

    monkeypatch.setattr(convert_generated, "run_jar", run_jar)

    with pytest.raises(RuntimeError, match="no output"):
        convert_bytes(STAND_IN_PDF, format="markdown")


@pytest.mark.parametrize(
    "failure",
    [
        None,
        subprocess.CalledProcessError(1, ["java"]),
        subprocess.TimeoutExpired(["java"], 1),
        KeyboardInterrupt(),
    ],
    ids=["success", "error of the JAR", "timeout", "interrupt"],
)
def test_the_temporary_folder_is_removed(failure, monkeypatch, tmp_path):
    convert_bytes = _convert_bytes()
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen, failure))

    if failure is None:
        convert_bytes(STAND_IN_PDF, format="markdown")
    else:
        with pytest.raises(type(failure)):
            convert_bytes(STAND_IN_PDF, format="markdown")

    assert seen, "the stand-in JAR did not run"
    assert list(tmp_path.iterdir()) == []


@pytest.mark.skipif(os.name == "nt", reason="POSIX permissions")
def test_the_temporary_files_are_private(monkeypatch, tmp_path):
    convert_bytes = _convert_bytes()
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    seen = []
    monkeypatch.setattr(convert_generated, "run_jar", _stand_in(seen))

    convert_bytes(STAND_IN_PDF, format="markdown")

    assert (seen[0]["folder_mode"], seen[0]["file_mode"]) == (0o700, 0o600)
