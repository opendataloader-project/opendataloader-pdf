"""Upload size limit tests for hybrid server endpoints."""

import os

import pytest

from opendataloader_pdf import hybrid_server


@pytest.mark.asyncio
async def test_convert_file_enforces_max_file_size(monkeypatch):
    """The conversion endpoint rejects oversized uploads before conversion."""
    from httpx import ASGITransport, AsyncClient

    monkeypatch.setattr(hybrid_server, "converter", object())
    app = hybrid_server.create_app(max_file_size=4)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/convert/file",
            files={"files": ("oversized.pdf", b"%PDF-1.4", "application/pdf")},
        )

    assert response.status_code == 413
    assert response.json()["status"] == "failure"
    assert "File size exceeds maximum allowed" in response.json()["errors"][0]
    assert "4 bytes" in response.json()["errors"][0]


@pytest.mark.asyncio
async def test_convert_file_rejects_uninitialized_server_before_temp_write(
    monkeypatch, tmp_path
):
    """The conversion endpoint should not persist uploads while uninitialized."""
    from httpx import ASGITransport, AsyncClient

    created_paths = []
    real_named_temporary_file = hybrid_server.tempfile.NamedTemporaryFile

    def tracking_named_temporary_file(*args, **kwargs):
        kwargs.setdefault("dir", tmp_path)
        tmp = real_named_temporary_file(*args, **kwargs)
        created_paths.append(tmp.name)
        return tmp

    monkeypatch.setattr(hybrid_server, "converter", None)
    monkeypatch.setattr(
        hybrid_server.tempfile, "NamedTemporaryFile", tracking_named_temporary_file
    )
    app = hybrid_server.create_app(max_file_size=0)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/convert/file",
            files={"files": ("accepted-by-size.pdf", b"%PDF-1.4", "application/pdf")},
        )

    assert response.status_code == 503
    assert response.json()["status"] == "failure"
    assert created_paths == []


@pytest.mark.asyncio
async def test_profile_file_enforces_max_file_size_before_converter_initialization():
    """The profiling endpoint must apply the same upload limit as conversion."""
    from httpx import ASGITransport, AsyncClient

    app = hybrid_server.create_app(max_file_size=4)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/profile/file",
            files={"files": ("oversized.pdf", b"%PDF-1.4", "application/pdf")},
        )

    assert response.status_code == 413
    assert response.json()["status"] == "failure"
    assert "File size exceeds maximum allowed" in response.json()["errors"][0]
    assert "4 bytes" in response.json()["errors"][0]
    assert app.state.profile_converters_initialized is False


@pytest.mark.asyncio
async def test_profile_file_removes_temp_file_when_upload_exceeds_max_size(
    monkeypatch, tmp_path
):
    """Oversized profile uploads should not leave partial temp files behind."""
    from httpx import ASGITransport, AsyncClient

    created_paths = []
    real_named_temporary_file = hybrid_server.tempfile.NamedTemporaryFile

    def tracking_named_temporary_file(*args, **kwargs):
        kwargs.setdefault("dir", tmp_path)
        tmp = real_named_temporary_file(*args, **kwargs)
        created_paths.append(tmp.name)
        return tmp

    monkeypatch.setattr(
        hybrid_server.tempfile, "NamedTemporaryFile", tracking_named_temporary_file
    )

    app = hybrid_server.create_app(max_file_size=4)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/profile/file",
            files={"files": ("oversized.pdf", b"%PDF-1.4", "application/pdf")},
        )

    assert response.status_code == 413
    assert created_paths
    assert all(not os.path.exists(path) for path in created_paths)
    assert app.state.profile_converters_initialized is False


@pytest.mark.asyncio
async def test_profile_file_keeps_initialization_flag_false_when_converter_init_fails(
    monkeypatch,
):
    """Profile initialization is marked complete only after all converters exist."""
    from httpx import ASGITransport, AsyncClient

    def fail_converter_initialization(*args, **kwargs):
        raise RuntimeError("profile converter init failed")

    monkeypatch.setattr(
        hybrid_server, "create_converter", fail_converter_initialization
    )
    app = hybrid_server.create_app(max_file_size=0)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/profile/file",
            files={"files": ("profile.pdf", b"%PDF-1.4", "application/pdf")},
        )

    assert response.status_code == 500
    assert response.json()["status"] == "failure"
    assert app.state.profile_converters_initialized is False
