from pathlib import Path

import pytest


@pytest.fixture
def input_pdf():
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "lorem.pdf"


@pytest.fixture
def input_pdf_academic():
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "1901.03003.pdf"
