"""Unit tests for JobManager — no MCP dependency."""
import threading
import time
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

from opendataloader_pdf_mcp.jobs import Job, JobManager, JobStatus


@pytest.fixture
def manager():
    return JobManager()


@pytest.fixture
def pdf_file(tmp_path):
    f = tmp_path / "test.pdf"
    f.write_bytes(b"%PDF-1.4 test content")
    return f


class TestSubmit:
    def test_submit_returns_job_id(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = manager.submit(str(pdf_file), "markdown")
        assert isinstance(job_id, str) and len(job_id) == 36  # UUID4

    def test_submit_missing_file_raises(self, manager):
        with pytest.raises(FileNotFoundError):
            manager.submit("/nonexistent/file.pdf", "markdown")

    def test_submit_bad_format_raises(self, manager, pdf_file):
        with pytest.raises(ValueError, match="Unsupported format"):
            manager.submit(str(pdf_file), "docx")

    def test_duplicate_hash_returns_same_job_when_done(self, manager, pdf_file):
        done_event = threading.Event()

        def fake_convert(**kwargs):
            done_event.wait()

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            job_id1 = manager.submit(str(pdf_file), "markdown")
            # Force job to DONE state directly for dedup test
            job = manager.get(job_id1)
            job.status = JobStatus.DONE
            job.artifact = "content"
            manager._hash_index[job.content_hash] = job_id1
            done_event.set()

            job_id2 = manager.submit(str(pdf_file), "markdown")

        assert job_id1 == job_id2

    def test_failed_job_not_deduped(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock(side_effect=RuntimeError("boom"))
            job_id1 = manager.submit(str(pdf_file), "markdown")
            # Wait for worker to fail
            for _ in range(50):
                if manager.get(job_id1).status == JobStatus.FAILED:
                    break
                time.sleep(0.05)
            assert manager.get(job_id1).status == JobStatus.FAILED

            job_id2 = manager.submit(str(pdf_file), "markdown")

        assert job_id1 != job_id2


class TestGet:
    def test_get_unknown_job_raises(self, manager):
        with pytest.raises(KeyError, match="unknown job_id"):
            manager.get("no-such-id")

    def test_get_returns_job(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = manager.submit(str(pdf_file), "markdown")
        job = manager.get(job_id)
        assert isinstance(job, Job)
        assert job.job_id == job_id


class TestCancel:
    def test_cancel_unknown_job_raises(self, manager):
        with pytest.raises(KeyError, match="unknown job_id"):
            manager.cancel("no-such-id")

    def test_cancel_running_job(self, manager, pdf_file):
        blocker = threading.Event()

        def slow_convert(**kwargs):
            blocker.wait(timeout=5)

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = slow_convert
            job_id = manager.submit(str(pdf_file), "markdown")
            # Wait until running
            for _ in range(50):
                if manager.get(job_id).status == JobStatus.RUNNING:
                    break
                time.sleep(0.02)
            manager.cancel(job_id)
            blocker.set()

        for _ in range(50):
            if manager.get(job_id).status == JobStatus.CANCELLED:
                break
            time.sleep(0.02)
        assert manager.get(job_id).status == JobStatus.CANCELLED
        assert manager.get(job_id).completed_at is not None

    def test_cancel_pending_job(self, manager, pdf_file):
        slow_start = threading.Event()

        original_run = manager._run

        def delayed_run(job, input_file):
            slow_start.wait(timeout=5)
            original_run(job, input_file)

        manager._run = delayed_run

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = manager.submit(str(pdf_file), "markdown")
            # Cancel while still PENDING (thread blocked before _run executes)
            status = manager.cancel(job_id)

        assert status == JobStatus.CANCELLED
        assert manager.get(job_id).status == JobStatus.CANCELLED
        assert manager.get(job_id).completed_at is not None
        slow_start.set()  # release the blocked thread

    def test_cancel_done_job_is_noop(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = manager.submit(str(pdf_file), "markdown")
            job = manager.get(job_id)
            job.status = JobStatus.DONE

        status = manager.cancel(job_id)
        assert status == JobStatus.DONE


class TestWorker:
    def test_job_completes_with_artifact(self, manager, pdf_file, tmp_path):
        artifact_content = "# Converted content"

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            out = Path(output_dir) / f"{stem}.md"
            out.write_text(artifact_content, encoding="utf-8")

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            job_id = manager.submit(str(pdf_file), "markdown")
            for _ in range(100):
                if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                    break
                time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.artifact == artifact_content
        assert job.completed_at is not None

    def test_job_fails_on_exception(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock(side_effect=RuntimeError("parse error"))
            job_id = manager.submit(str(pdf_file), "markdown")
            for _ in range(100):
                if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                    break
                time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.FAILED
        assert "parse error" in job.error
        assert job.completed_at is not None
