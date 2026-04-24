"""Tests for the four async MCP tools in server.py."""
import threading
import time
from unittest.mock import MagicMock, patch

import pytest

from opendataloader_pdf_mcp.jobs import JobStatus
from opendataloader_pdf_mcp.server import cancel_job, get_artifact, get_job_status, submit_pdf, _job_manager


@pytest.fixture(autouse=True)
def reset_manager():
    """Clear job manager state between tests."""
    _job_manager._jobs.clear()
    _job_manager._hash_index.clear()
    yield
    _job_manager._jobs.clear()
    _job_manager._hash_index.clear()


@pytest.fixture
def pdf_file(tmp_path):
    f = tmp_path / "sample.pdf"
    f.write_bytes(b"%PDF-1.4 test")
    return f


class TestSubmitPdf:
    def test_missing_file_raises(self):
        with pytest.raises(FileNotFoundError):
            submit_pdf(input_path="/no/such/file.pdf")

    def test_bad_format_raises(self, pdf_file):
        with pytest.raises(ValueError, match="Unsupported format"):
            submit_pdf(input_path=str(pdf_file), format="docx")

    def test_returns_job_id_and_pending_status(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            result = submit_pdf(input_path=str(pdf_file))
        assert "job_id" in result
        assert result["status"] == "pending"
        assert "content_hash" in result


class TestGetJobStatus:
    def test_unknown_id_raises(self):
        with pytest.raises(KeyError, match="unknown job_id"):
            get_job_status(job_id="no-such-id")

    def test_returns_status_fields(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
        result = get_job_status(job_id=job_id)
        assert result["job_id"] == job_id
        assert result["status"] in ("pending", "running", "done", "failed", "cancelled")
        assert "triage_decision" in result
        assert "score" in result
        assert "submitted_at" in result
        assert "completed_at" in result


class TestCancelJob:
    def test_unknown_id_raises(self):
        with pytest.raises(KeyError, match="unknown job_id"):
            cancel_job(job_id="no-such-id")

    def test_cancel_running_job(self, pdf_file):
        blocker = threading.Event()

        def slow_convert(**kwargs):
            blocker.wait(timeout=5)

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = slow_convert
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            for _ in range(50):
                if get_job_status(job_id=job_id)["status"] == "running":
                    break
                time.sleep(0.02)
            result = cancel_job(job_id=job_id)
            blocker.set()

        assert result["job_id"] == job_id
        assert result["status"] == "cancelled"

    def test_cancel_idempotent_on_done_job(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
        result = cancel_job(job_id=job_id)
        assert result["status"] == "done"


class TestGetArtifact:
    def test_unknown_id_raises(self):
        with pytest.raises(KeyError, match="unknown job_id"):
            get_artifact(job_id="no-such-id")

    def test_not_done_raises(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.RUNNING
        with pytest.raises(RuntimeError, match="not done"):
            get_artifact(job_id=job_id)

    def test_failed_job_raises(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.FAILED
                job.error = "something broke"
        with pytest.raises(RuntimeError, match="failed"):
            get_artifact(job_id=job_id)

    def test_cancelled_job_raises(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.CANCELLED
        with pytest.raises(RuntimeError, match="cancelled"):
            get_artifact(job_id=job_id)

    def test_done_job_returns_artifact(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Converted"
        result = get_artifact(job_id=job_id)
        assert result == "# Converted"


class TestJobResource:
    def test_resource_returns_artifact_when_done(self, pdf_file):
        from opendataloader_pdf_mcp.server import get_job_resource
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Resource content"
        result = get_job_resource(job_id=job_id)
        assert result == "# Resource content"

    def test_resource_returns_error_string_when_not_done(self, pdf_file):
        from opendataloader_pdf_mcp.server import get_job_resource
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.RUNNING
        result = get_job_resource(job_id=job_id)
        assert "not done" in result or "running" in result

    def test_resource_returns_error_string_for_unknown_id(self):
        from opendataloader_pdf_mcp.server import get_job_resource
        result = get_job_resource(job_id="no-such-id")
        assert "unknown" in result.lower() or "not found" in result.lower()
