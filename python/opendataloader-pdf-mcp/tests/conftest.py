"""Shared test fixtures for opendataloader-pdf-mcp tests."""

from pathlib import Path

import pytest


@pytest.fixture
def input_pdf():
    """Return path to the sample lorem PDF."""
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "lorem.pdf"


@pytest.fixture
def input_pdf_academic():
    """Return path to the sample academic PDF."""
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "1901.03003.pdf"
