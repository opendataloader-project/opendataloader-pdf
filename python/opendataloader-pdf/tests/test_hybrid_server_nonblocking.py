"""Tests for hybrid_server non-blocking conversion.

Verifies that converter.convert() runs in a thread pool rather than blocking
the event loop. This is the root cause of issue #301: the Java client's
3-second health check times out when the server is busy with a synchronous
conversion call inside an async endpoint.
"""

import asyncio
import threading
import time
from unittest.mock import MagicMock, patch

import pytest


@pytest.fixture
def mock_docling():
    """Mock docling modules so tests don't need the actual dependency."""
    mock_converter = MagicMock()
    mock_result = MagicMock()
    mock_result.status.value = "success"
    mock_result.errors = []
    mock_result.input.page_count = 1
    mock_result.document.export_to_dict.return_value = {
        "pages": {"1": {}},
        "body": {},
    }

    # Track which thread the conversion runs on
    convert_thread_name = {}

    def tracking_convert(path, page_range=None):
        convert_thread_name["thread"] = threading.current_thread().name
        time.sleep(2)
        return mock_result

    mock_converter.convert = tracking_convert
    mock_converter._convert_thread = convert_thread_name

    mock_conversion_status = MagicMock()
    mock_conversion_status.PARTIAL_SUCCESS = "partial_success"

    with patch.dict("sys.modules", {
        "docling": MagicMock(),
        "docling.datamodel.base_models": MagicMock(
            InputFormat=MagicMock(PDF="pdf"),
            ConversionStatus=mock_conversion_status,
        ),
        "docling.datamodel.pipeline_options": MagicMock(),
        "docling.document_converter": MagicMock(),
        "uvicorn": MagicMock(),
    }):
        yield mock_converter


@pytest.fixture
def app_with_converter(mock_docling):
    """Create a FastAPI app with the mock converter."""
    import importlib
    from opendataloader_pdf import hybrid_server

    importlib.reload(hybrid_server)

    app = hybrid_server.create_app()
    hybrid_server.converter = mock_docling
    return app


@pytest.mark.asyncio
async def test_convert_runs_in_thread_pool(app_with_converter, mock_docling):
    """converter.convert() must run in a worker thread, not on the event loop.

    When converter.convert() runs directly on the async event loop thread,
    it blocks all concurrent request handling — including /health checks.
    The fix wraps converter.convert() with asyncio.to_thread() so it runs
    in a separate thread.

    Reproduces issue #301.
    """
    from httpx import ASGITransport, AsyncClient

    transport = ASGITransport(app=app_with_converter)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        event_loop_thread = threading.current_thread().name

        response = await client.post(
            "/v1/convert/file",
            files={"files": ("test.pdf", b"%PDF-1.4 minimal", "application/pdf")},
        )

        assert response.status_code == 200

        convert_thread = mock_docling._convert_thread.get("thread")
        assert convert_thread is not None, "converter.convert() was not called"
        assert convert_thread != event_loop_thread, (
            f"converter.convert() ran on the event loop thread '{event_loop_thread}'. "
            f"It must run in a worker thread via asyncio.to_thread() to avoid "
            f"blocking /health and other endpoints during long conversions."
        )


@pytest.mark.asyncio
async def test_health_responds_during_conversion(app_with_converter):
    """Health endpoint must respond quickly even during active conversion.

    This is the user-facing symptom of issue #301: the Java CLI gets
    SocketTimeoutException when the hybrid server is busy processing
    another document. Mock sleep is 2s; health must respond under 0.2s.
    """
    from httpx import ASGITransport, AsyncClient

    transport = ASGITransport(app=app_with_converter)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Start conversion (takes 2s in mock)
        convert_task = asyncio.create_task(
            client.post(
                "/v1/convert/file",
                files={"files": ("test.pdf", b"%PDF-1.4 minimal", "application/pdf")},
            )
        )

        # Wait for conversion to start in the worker thread
        await asyncio.sleep(0.3)

        # Health check should respond quickly — well under the 2s conversion
        start = time.monotonic()
        health_response = await client.get("/health")
        health_time = time.monotonic() - start

        assert health_response.status_code == 200
        assert health_response.json() == {"status": "ok"}
        assert health_time < 0.2, (
            f"Health endpoint took {health_time:.2f}s during conversion. "
            f"Expected < 0.2s. The event loop is likely blocked."
        )

        convert_response = await convert_task
        assert convert_response.status_code == 200


@pytest.mark.asyncio
async def test_profile_picture_converter_forces_picture_images(mock_docling):
    """The picture profile must still enable image generation when global attachment is off."""
    from httpx import ASGITransport, AsyncClient
    import importlib
    from opendataloader_pdf import hybrid_server

    importlib.reload(hybrid_server)

    captured_calls = []
    mock_result = MagicMock()
    mock_result.document.export_to_dict.return_value = {
        "pictures": [],
        "texts": [],
        "tables": [],
    }
    mock_profile_converter = MagicMock()
    mock_profile_converter.convert.return_value = mock_result

    def fake_create_converter(**kwargs):
        captured_calls.append(kwargs)
        return mock_profile_converter

    with patch.object(
        hybrid_server,
        "create_converter",
        side_effect=fake_create_converter,
    ):
        app = hybrid_server.create_app(generate_picture_images=False)
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/v1/profile/file",
                files={"files": ("test.pdf", b"%PDF-1.4 minimal", "application/pdf")},
            )

        assert response.status_code == 200

    picture_calls = [call for call in captured_calls if call.get("enrich_picture_description")]
    assert picture_calls, "Expected at least one picture profile converter call"
    assert any(call.get("generate_picture_images") is True for call in picture_calls)
