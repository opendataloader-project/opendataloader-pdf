import shutil
import sys
from pathlib import Path

import pytest


SRC_DIR = Path(__file__).resolve().parents[1] / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))


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
