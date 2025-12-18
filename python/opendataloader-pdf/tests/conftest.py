import shutil
from pathlib import Path

import pytest


@pytest.fixture
def input_pdf():
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "1901.03003.pdf"


@pytest.fixture
def output_dir():
    path = (
        Path(__file__).resolve().parents[3]
        / "python"
        / "opendataloader-pdf"
        / "tests"
        / "temp"
    )
    path.mkdir(exist_ok=True)
    yield path
    shutil.rmtree(path, ignore_errors=True)
