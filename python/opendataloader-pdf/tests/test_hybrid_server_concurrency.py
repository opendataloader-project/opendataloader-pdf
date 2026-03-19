"""Concurrency tests for the hybrid FastAPI server."""

from __future__ import annotations

import sys
import threading
from types import SimpleNamespace

from fastapi.testclient import TestClient

from opendataloader_pdf import hybrid_server


def _successful_result():
    return SimpleNamespace(
        status=SimpleNamespace(value="success"),
        errors=[],
        input=SimpleNamespace(page_count=1),
        document=SimpleNamespace(export_to_dict=lambda: {"pages": {"1": {}}}),
    )


class BlockingConverter:
    """Test double that blocks until the test releases the conversion."""

    def __init__(self, started: threading.Event, release: threading.Event):
        self.started = started
        self.release = release

    def convert(self, input_path, page_range=None):  # noqa: ANN001
        self.started.set()
        if not self.release.wait(timeout=5):
            raise TimeoutError("Timed out waiting for test to release conversion")
        return _successful_result()


def test_health_check_stays_responsive_during_conversion(monkeypatch):
    """Long-running conversion requests should not block /health."""
    started = threading.Event()
    release = threading.Event()
    fake_status = SimpleNamespace(PARTIAL_SUCCESS=object())

    monkeypatch.setitem(
        sys.modules,
        "docling.datamodel.base_models",
        SimpleNamespace(ConversionStatus=fake_status),
    )

    monkeypatch.setattr(
        hybrid_server,
        "create_converter",
        lambda **_: BlockingConverter(started, release),
    )

    app = hybrid_server.create_app()
    files = {"files": ("sample.pdf", b"%PDF-1.4", "application/pdf")}
    results = {}

    with TestClient(app) as client:
        convert_thread = threading.Thread(
            target=lambda: results.setdefault(
                "convert", client.post("/v1/convert/file", files=files)
            )
        )
        health_done = threading.Event()

        def request_health():
            results["health"] = client.get("/health")
            health_done.set()

        health_thread = threading.Thread(target=request_health)

        convert_thread.start()
        assert started.wait(timeout=1), "Conversion request never reached the converter"

        try:
            health_thread.start()
            assert health_done.wait(
                timeout=1
            ), "/health should remain responsive while conversion is running"
            assert results["health"].status_code == 200
        finally:
            release.set()
            convert_thread.join(timeout=5)
            health_thread.join(timeout=5)

    assert results["convert"].status_code == 200
