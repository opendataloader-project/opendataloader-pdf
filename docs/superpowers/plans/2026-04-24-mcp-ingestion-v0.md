# MCP Ingestion v0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the `opendataloader-pdf-mcp` Python MCP server with async job management: submit a PDF for conversion, poll status, retrieve the artifact, and cancel in-flight jobs.

**Architecture:** A new `jobs.py` module contains `JobStatus`, `Job`, and `JobManager` with no MCP dependency — all logic lives here, including background threading and content-hash deduplication. `server.py` stays thin: it imports the `JobManager` singleton and registers four new tools plus one URI-template resource handler. Existing `convert_pdf` tool is unchanged.

**Tech Stack:** Python 3.10+, FastMCP (`mcp[cli]>=1.27`), `threading`, `hashlib`, `uuid`, `dataclasses`, `enum` (all stdlib). Tests use pytest (already in project).

---

## File Structure

### Create
- `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py` — `JobStatus` enum, `Job` dataclass, `JobManager` class
- `python/opendataloader-pdf-mcp/tests/test_jobs.py` — unit tests for `JobManager` (no MCP)
- `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py` — integration tests for the four new tools and resource handler

### Modify
- `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py` — import `_job_manager` singleton, add four tools, add resource handler, extract `_build_kwargs` helper

---

## Task 1: Job data model and JobManager

**Files:**
- Create: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py`
- Create: `python/opendataloader-pdf-mcp/tests/test_jobs.py`

- [ ] **Step 1: Write failing tests for JobManager**

Create `python/opendataloader-pdf-mcp/tests/test_jobs.py`:

```python
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

    def test_cancel_done_job_is_noop(self, manager, pdf_file):
        job_id = manager.submit.__func__  # just to get a ref; override below
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_jobs.py -v 2>&1 | head -30
```
Expected: `ImportError` — `jobs` module does not exist yet.

- [ ] **Step 3: Create `jobs.py`**

Create `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py`:

```python
"""Async job management for opendataloader-pdf-mcp."""

from __future__ import annotations

import hashlib
import tempfile
import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Any

import opendataloader_pdf

_SUPPORTED_FORMATS = {"json", "text", "html", "markdown", "markdown-with-html", "markdown-with-images"}
_EXT_MAP = {
    "json": ".json",
    "text": ".txt",
    "html": ".html",
    "markdown": ".md",
    "markdown-with-html": ".md",
    "markdown-with-images": ".md",
}


class JobStatus(str, Enum):
    PENDING   = "pending"
    RUNNING   = "running"
    DONE      = "done"
    FAILED    = "failed"
    CANCELLED = "cancelled"


@dataclass
class Job:
    job_id:          str
    content_hash:    str
    status:          JobStatus
    format:          str
    kwargs:          dict[str, Any]
    artifact:        str | None       = None
    triage_decision: str | None       = None
    score:           float | None     = None
    error:           str | None       = None
    submitted_at:    str              = field(default_factory=lambda: _now())
    completed_at:    str | None       = None
    _cancel_event:   threading.Event  = field(default_factory=threading.Event, repr=False)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class JobManager:
    def __init__(self) -> None:
        self._jobs: dict[str, Job] = {}
        self._hash_index: dict[str, str] = {}  # content_hash → job_id (DONE only)
        self._lock = threading.Lock()

    def submit(self, path: str, format: str, **kwargs: Any) -> str:
        input_file = Path(path).expanduser().resolve()
        if not input_file.is_file():
            raise FileNotFoundError(f"Input file not found: {path}")
        if format not in _SUPPORTED_FORMATS:
            raise ValueError(
                f"Unsupported format: {format!r}. Supported: {', '.join(sorted(_SUPPORTED_FORMATS))}"
            )

        content_hash = hashlib.sha256(input_file.read_bytes()).hexdigest()

        with self._lock:
            existing_id = self._hash_index.get(content_hash)
            if existing_id and self._jobs.get(existing_id, Job("", "", JobStatus.FAILED, "", {})).status == JobStatus.DONE:
                return existing_id

            job_id = str(uuid.uuid4())
            job = Job(
                job_id=job_id,
                content_hash=content_hash,
                status=JobStatus.PENDING,
                format=format,
                kwargs=kwargs,
            )
            self._jobs[job_id] = job

        t = threading.Thread(target=self._run, args=(job, input_file), daemon=True)
        t.start()
        return job_id

    def get(self, job_id: str) -> Job:
        try:
            return self._jobs[job_id]
        except KeyError:
            raise KeyError(f"unknown job_id: {job_id}")

    def cancel(self, job_id: str) -> JobStatus:
        job = self.get(job_id)
        if job.status in (JobStatus.DONE, JobStatus.FAILED, JobStatus.CANCELLED):
            return job.status
        job._cancel_event.set()
        return job.status

    def _run(self, job: Job, input_file: Path) -> None:
        if job._cancel_event.is_set():
            job.status = JobStatus.CANCELLED
            job.completed_at = _now()
            return

        job.status = JobStatus.RUNNING
        ext = _EXT_MAP[job.format]

        try:
            with tempfile.TemporaryDirectory() as tmp_dir:
                kwargs: dict[str, Any] = {
                    "input_path": str(input_file),
                    "output_dir": tmp_dir,
                    "format": job.format,
                    "quiet": True,
                    **job.kwargs,
                }
                opendataloader_pdf.convert(**kwargs)

                if job._cancel_event.is_set():
                    job.status = JobStatus.CANCELLED
                    job.completed_at = _now()
                    return

                output_file = Path(tmp_dir) / f"{input_file.stem}{ext}"
                if not output_file.is_file():
                    matches = sorted(f for f in Path(tmp_dir).iterdir() if f.suffix == ext)
                    if not matches:
                        raise RuntimeError(f"No '{ext}' output file generated")
                    output_file = matches[0]

                job.artifact = output_file.read_text(encoding="utf-8")

            with self._lock:
                self._hash_index[job.content_hash] = job.job_id

            job.status = JobStatus.DONE
            job.completed_at = _now()

        except Exception as exc:
            if job.status != JobStatus.CANCELLED:
                job.error = str(exc)
                job.status = JobStatus.FAILED
                job.completed_at = _now()
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_jobs.py -v
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py
git add python/opendataloader-pdf-mcp/tests/test_jobs.py
git commit -m "feat(mcp): add async job manager with content-hash deduplication"
```

---

## Task 2: Four async MCP tools

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`
- Create: `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py`

- [ ] **Step 1: Write failing tests for the four new tools**

Create `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py`:

```python
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
        for _ in range(50):
            if get_job_status(job_id=job_id)["status"] == "cancelled":
                break
            time.sleep(0.02)
        assert get_job_status(job_id=job_id)["status"] == "cancelled"

    def test_cancel_idempotent_on_done_job(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
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
            # Force to RUNNING to test the "not done" error
            _job_manager.get(job_id).status = JobStatus.RUNNING
        with pytest.raises(RuntimeError, match="not done"):
            get_artifact(job_id=job_id)

    def test_failed_job_raises(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            job.status = JobStatus.FAILED
            job.error = "something broke"
        with pytest.raises(RuntimeError, match="failed"):
            get_artifact(job_id=job_id)

    def test_cancelled_job_raises(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            _job_manager.get(job_id).status = JobStatus.CANCELLED
        with pytest.raises(RuntimeError, match="cancelled"):
            get_artifact(job_id=job_id)

    def test_done_job_returns_artifact(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            job.status = JobStatus.DONE
            job.artifact = "# Converted"
        result = get_artifact(job_id=job_id)
        assert result == "# Converted"
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_server_async_tools.py -v 2>&1 | head -20
```
Expected: `ImportError` — `submit_pdf`, `get_job_status`, `cancel_job`, `get_artifact`, `_job_manager` not yet in `server.py`.

- [ ] **Step 3: Extend `server.py` with four new tools**

Open `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`.

Add this import block at the top (after the existing imports):

```python
from opendataloader_pdf_mcp.jobs import JobManager, JobStatus

_job_manager = JobManager()
```

After the existing `convert_pdf` function (before `def main()`), add a private kwargs builder and the four new tools:

```python
def _collect_kwargs(
    password: str | None,
    pages: str | None,
    keep_line_breaks: bool,
    sanitize: bool,
    content_safety_off: str | None,
    replace_invalid_chars: str | None,
    use_struct_tree: bool,
    table_method: str | None,
    reading_order: str | None,
    markdown_page_separator: str | None,
    text_page_separator: str | None,
    html_page_separator: str | None,
    image_output: str | None,
    image_format: str | None,
    include_header_footer: bool,
    detect_strikethrough: bool,
    hybrid: str | None,
    hybrid_mode: str | None,
    hybrid_url: str | None,
    hybrid_timeout: str | None,
    hybrid_fallback: bool,
    image_dir: str | None,
    fmt: str,
) -> dict:
    kwargs: dict = {}
    if password is not None:
        kwargs["password"] = password
    if pages is not None:
        kwargs["pages"] = pages
    if keep_line_breaks:
        kwargs["keep_line_breaks"] = True
    if sanitize:
        kwargs["sanitize"] = True
    if content_safety_off is not None:
        kwargs["content_safety_off"] = content_safety_off
    if replace_invalid_chars is not None:
        kwargs["replace_invalid_chars"] = replace_invalid_chars
    if use_struct_tree:
        kwargs["use_struct_tree"] = True
    if table_method is not None:
        kwargs["table_method"] = table_method
    if reading_order is not None:
        kwargs["reading_order"] = reading_order
    if markdown_page_separator is not None:
        kwargs["markdown_page_separator"] = markdown_page_separator
    if text_page_separator is not None:
        kwargs["text_page_separator"] = text_page_separator
    if html_page_separator is not None:
        kwargs["html_page_separator"] = html_page_separator
    if fmt == "markdown-with-images" and image_output is None:
        kwargs["image_output"] = "embedded"
    elif image_output is not None:
        kwargs["image_output"] = image_output
    if image_format is not None:
        kwargs["image_format"] = image_format
    if include_header_footer:
        kwargs["include_header_footer"] = True
    if detect_strikethrough:
        kwargs["detect_strikethrough"] = True
    if hybrid is not None:
        kwargs["hybrid"] = hybrid
    if hybrid_mode is not None:
        kwargs["hybrid_mode"] = hybrid_mode
    if hybrid_url is not None:
        kwargs["hybrid_url"] = hybrid_url
    if hybrid_timeout is not None:
        kwargs["hybrid_timeout"] = hybrid_timeout
    if hybrid_fallback:
        kwargs["hybrid_fallback"] = True
    if image_dir is not None:
        kwargs["image_dir"] = image_dir
    return kwargs


@mcp.tool()
def submit_pdf(
    input_path: str,
    format: str = "markdown",
    password: str | None = None,
    pages: str | None = None,
    keep_line_breaks: bool = False,
    sanitize: bool = False,
    content_safety_off: str | None = None,
    replace_invalid_chars: str | None = None,
    use_struct_tree: bool = False,
    table_method: str | None = None,
    reading_order: str | None = None,
    markdown_page_separator: str | None = None,
    text_page_separator: str | None = None,
    html_page_separator: str | None = None,
    image_output: str | None = None,
    image_format: str | None = None,
    include_header_footer: bool = False,
    detect_strikethrough: bool = False,
    hybrid: str | None = None,
    hybrid_mode: str | None = None,
    hybrid_url: str | None = None,
    hybrid_timeout: str | None = None,
    hybrid_fallback: bool = False,
    image_dir: str | None = None,
) -> dict:
    """Submit a PDF for async conversion. Returns a job_id to poll with get_job_status."""
    kwargs = _collect_kwargs(
        password, pages, keep_line_breaks, sanitize, content_safety_off,
        replace_invalid_chars, use_struct_tree, table_method, reading_order,
        markdown_page_separator, text_page_separator, html_page_separator,
        image_output, image_format, include_header_footer, detect_strikethrough,
        hybrid, hybrid_mode, hybrid_url, hybrid_timeout, hybrid_fallback,
        image_dir, format,
    )
    job_id = _job_manager.submit(input_path, format, **kwargs)
    job = _job_manager.get(job_id)
    return {"job_id": job_id, "status": job.status.value, "content_hash": job.content_hash}


@mcp.tool()
def get_job_status(job_id: str) -> dict:
    """Get the current status and metadata of a submitted conversion job."""
    job = _job_manager.get(job_id)
    return {
        "job_id": job.job_id,
        "status": job.status.value,
        "triage_decision": job.triage_decision,
        "score": job.score,
        "submitted_at": job.submitted_at,
        "completed_at": job.completed_at,
    }


@mcp.tool()
def cancel_job(job_id: str) -> dict:
    """Cancel a pending or running conversion job. Idempotent on terminal jobs."""
    status = _job_manager.cancel(job_id)
    return {"job_id": job_id, "status": status.value}


@mcp.tool()
def get_artifact(job_id: str) -> str:
    """Retrieve the converted content for a completed job. Raises if not done."""
    job = _job_manager.get(job_id)
    if job.status == JobStatus.FAILED:
        raise RuntimeError(f"job {job_id} failed: {job.error}")
    if job.status == JobStatus.CANCELLED:
        raise RuntimeError(f"job {job_id} was cancelled")
    if job.status != JobStatus.DONE:
        raise RuntimeError(f"job {job_id} is {job.status.value}, not done")
    return job.artifact
```

- [ ] **Step 4: Run new tests to verify they pass**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_server_async_tools.py -v
```
Expected: all pass.

- [ ] **Step 5: Run existing tests to verify no regressions**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_convert_pdf.py -v
```
Expected: all pass (convert_pdf tool unchanged).

- [ ] **Step 6: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py
git add python/opendataloader-pdf-mcp/tests/test_server_async_tools.py
git commit -m "feat(mcp): add submit_pdf, get_job_status, cancel_job, get_artifact tools"
```

---

## Task 3: MCP resource handler

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`

- [ ] **Step 1: Write failing test for resource handler**

Append to `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py`:

```python
class TestJobResource:
    def test_resource_returns_artifact_when_done(self, pdf_file):
        from opendataloader_pdf_mcp.server import get_job_resource
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            job.status = JobStatus.DONE
            job.artifact = "# Resource content"
        result = get_job_resource(job_id=job_id)
        assert result == "# Resource content"

    def test_resource_returns_error_string_when_not_done(self, pdf_file):
        from opendataloader_pdf_mcp.server import get_job_resource
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            _job_manager.get(job_id).status = JobStatus.RUNNING
        result = get_job_resource(job_id=job_id)
        assert "not done" in result or "running" in result

    def test_resource_returns_error_string_for_unknown_id(self):
        from opendataloader_pdf_mcp.server import get_job_resource
        result = get_job_resource(job_id="no-such-id")
        assert "unknown" in result.lower() or "not found" in result.lower()
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/test_server_async_tools.py::TestJobResource -v 2>&1 | head -20
```
Expected: `ImportError` — `get_job_resource` not yet defined.

- [ ] **Step 3: Add resource handler to `server.py`**

In `server.py`, add after the `get_artifact` tool (before `def main()`):

```python
@mcp.resource("jobs://{job_id}")
def get_job_resource(job_id: str) -> str:
    """MCP resource: returns artifact content for a completed job."""
    try:
        job = _job_manager.get(job_id)
    except KeyError:
        return f"Error: job not found: {job_id}"
    if job.status != JobStatus.DONE:
        return f"Error: job {job_id} is {job.status.value}, not done"
    return job.artifact
```

- [ ] **Step 4: Run all tests**

```bash
cd python/opendataloader-pdf-mcp
uv run pytest tests/ -v
```
Expected: all pass — `test_jobs.py`, `test_server_async_tools.py`, `test_convert_pdf.py`.

- [ ] **Step 5: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py
git add python/opendataloader-pdf-mcp/tests/test_server_async_tools.py
git commit -m "feat(mcp): add jobs resource handler for mcp resource protocol clients"
```

---

## Final Verification Checklist

- [ ] All three test files pass: `uv run pytest tests/ -v` from `python/opendataloader-pdf-mcp/`
- [ ] `submit_pdf` accepts same options as `convert_pdf` — check both share the same parameter list
- [ ] `_collect_kwargs` DRYs up both tools — verify `convert_pdf` still uses its own inline logic (no regression)
- [ ] Resource URI template `jobs://{job_id}` registered — verify with `uv run python3 -c "from opendataloader_pdf_mcp.server import mcp; print(mcp.list_resource_templates())"`

---

## Spec Coverage Check

| Spec section | Plan coverage |
|---|---|
| Async submit → job_id | Task 1 (`JobManager.submit`) + Task 2 (`submit_pdf` tool) |
| In-memory job store | Task 1 (`JobManager._jobs` dict) |
| Content-hash dedup (DONE only) | Task 1 (`_hash_index`, dedup test) |
| Background thread per job | Task 1 (`threading.Thread`, worker tests) |
| cancel_event checked pre/post convert | Task 1 (`_run` method) |
| `get_job_status` with triage fields | Task 2 |
| `cancel_job` idempotent | Task 2 |
| `get_artifact` tool | Task 2 |
| `resource://jobs/{job_id}` handler | Task 3 |
| Error handling table | Task 1 + Task 2 |
| Existing `convert_pdf` unchanged | Task 2 step 5 regression check |
