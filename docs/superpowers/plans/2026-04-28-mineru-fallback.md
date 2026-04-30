# MinerU Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in MinerU subprocess fallback to the async MCP job layer that triggers automatically when Java extraction produces a `HARD_FAIL` triage decision, storing the MinerU output as the primary artifact while preserving the original Java output in a separate field.

**Architecture:** A new `MinerURunner` class in `mineru.py` invokes the `mineru` CLI via `subprocess.run` and returns markdown + JSON output. `JobManager._run()` checks for `HARD_FAIL` after Java extraction and, when `enable_mineru_fallback=True`, calls `MinerURunner` and updates the job fields atomically using the existing `_status_lock` pattern. The `get_artifact` tool gains a `source` parameter to expose all three artifact slots (primary, java, mineru-json).

**Tech Stack:** Python 3.10+, `subprocess`, `shutil`, `unittest.mock`, `pytest`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/mineru.py` | Create | `MinerUResult` dataclass, `MinerUError` exception, `MinerURunner.run()` |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py` | Modify | 4 new `Job` fields; fallback branch in `_run()`; `enable_mineru_fallback` param in `submit()` |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py` | Modify | `enable_mineru_fallback` param on `submit_pdf`; `source` param on `get_artifact` |
| `python/opendataloader-pdf-mcp/tests/test_mineru.py` | Create | 4 unit tests for `MinerURunner` |
| `python/opendataloader-pdf-mcp/tests/test_jobs.py` | Modify | 5 new tests for fallback branch in `JobManager` |
| `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py` | Modify | 4 new tests for `get_artifact` source param |

---

## Task 1: `MinerURunner` — core subprocess wrapper

**Files:**
- Create: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/mineru.py`
- Create: `python/opendataloader-pdf-mcp/tests/test_mineru.py`

All commands run from `python/opendataloader-pdf-mcp/`.

- [x] **Step 1: Write the four failing tests**

Create `tests/test_mineru.py`:

```python
"""Unit tests for MinerURunner."""
import subprocess
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from opendataloader_pdf_mcp.mineru import MinerUError, MinerUResult, MinerURunner


@pytest.fixture
def pdf_path(tmp_path):
    p = tmp_path / "doc.pdf"
    p.write_bytes(b"%PDF-1.4")
    return p


class TestMinerURunner:
    def test_run_success(self, pdf_path, tmp_path):
        md_file = tmp_path / "doc.md"
        json_file = tmp_path / "doc.json"
        md_file.write_text("# Extracted", encoding="utf-8")
        json_file.write_text('{"pages": []}', encoding="utf-8")

        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stderr = ""

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                result = MinerURunner().run(pdf_path, tmp_path)

        assert isinstance(result, MinerUResult)
        assert result.markdown == "# Extracted"
        assert result.json_str == '{"pages": []}'
        assert result.exit_code == 0

    def test_run_not_in_path(self, pdf_path, tmp_path):
        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value=None):
            with pytest.raises(MinerUError, match="not found in PATH"):
                MinerURunner().run(pdf_path, tmp_path)

    def test_run_nonzero_exit(self, pdf_path, tmp_path):
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_result.stderr = "fatal: cannot open file"

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                with pytest.raises(MinerUError, match="exited 1"):
                    MinerURunner().run(pdf_path, tmp_path)

    def test_run_missing_output(self, pdf_path, tmp_path):
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stderr = ""

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                with pytest.raises(MinerUError, match="no output"):
                    MinerURunner().run(pdf_path, tmp_path)
```

- [x] **Step 2: Run tests to confirm they all fail**

```bash
.venv/bin/python -m pytest tests/test_mineru.py -v
```

Expected: 4 errors — `ModuleNotFoundError: No module named 'opendataloader_pdf_mcp.mineru'`

- [x] **Step 3: Implement `mineru.py`**

Create `src/opendataloader_pdf_mcp/mineru.py`:

```python
"""MinerU subprocess wrapper."""
from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


class MinerUError(Exception):
    pass


@dataclass
class MinerUResult:
    markdown:  str
    json_str:  str
    exit_code: int
    stderr:    str


class MinerURunner:
    def run(self, pdf_path: Path, output_dir: Path) -> MinerUResult:
        if shutil.which("mineru") is None:
            raise MinerUError("mineru not found in PATH")

        result = subprocess.run(
            ["mineru", "-p", str(pdf_path), "-o", str(output_dir), "--method", "auto"],
            capture_output=True,
            text=True,
        )

        if result.returncode != 0:
            raise MinerUError(f"mineru exited {result.returncode}: {result.stderr[:500]}")

        stem = pdf_path.stem
        md_file = output_dir / f"{stem}.md"
        json_file = output_dir / f"{stem}.json"

        if not md_file.is_file() or not json_file.is_file():
            # MinerU may write into a subdirectory named after the stem
            sub = output_dir / stem
            md_file = sub / f"{stem}.md"
            json_file = sub / f"{stem}.json"

        if not md_file.is_file() or not json_file.is_file():
            raise MinerUError("mineru produced no output")

        return MinerUResult(
            markdown=md_file.read_text(encoding="utf-8"),
            json_str=json_file.read_text(encoding="utf-8"),
            exit_code=result.returncode,
            stderr=result.stderr,
        )
```

- [x] **Step 4: Run tests to confirm they pass**

```bash
.venv/bin/python -m pytest tests/test_mineru.py -v
```

Expected: 4 passed

- [x] **Step 5: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/mineru.py \
        python/opendataloader-pdf-mcp/tests/test_mineru.py
git commit -m "feat(mcp): add MinerURunner subprocess wrapper with tests"
```

> **Note (actual impl):** `mineru.py` also adds `timeout=300` to `subprocess.run` and uses `content_list.json` (not `{stem}.json`) as the JSON filename, matching MinerU 2.x output layout. Commit: `fix(mcp): fix MinerU output path, add timeout, improve tests`.

---

## Task 2: Extend `Job` dataclass + `JobManager` fallback branch

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py`
- Modify: `python/opendataloader-pdf-mcp/tests/test_jobs.py`

All commands run from `python/opendataloader-pdf-mcp/`.

- [x] **Step 1: Write the five failing tests**

Append a new `TestMinerUFallback` class to `tests/test_jobs.py`:

```python
class TestMinerUFallback:
    """Tests for the MinerU fallback branch in JobManager._run()."""

    def test_submit_mineru_fallback_hard_fail(self, manager, pdf_file):
        java_content = "# Java output"
        mineru_content = "# MinerU output"
        mineru_json = '{"pages": []}'

        from opendataloader_pdf_mcp.mineru import MinerUResult

        # fake_convert writes the Java output file AND sets triage_decision=HARD_FAIL
        # on the job so _run() sees it deterministically (no timing dependency).
        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text(java_content, encoding="utf-8")
            # find the job that owns this input_file and inject HARD_FAIL
            for job in manager._jobs.values():
                if str(job.kwargs.get("enable_mineru_fallback")) or True:
                    job.triage_decision = "HARD_FAIL"

        mock_result = MinerUResult(
            markdown=mineru_content, json_str=mineru_json, exit_code=0, stderr=""
        )

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner_cls:
                mock_runner_cls.return_value.run.return_value = mock_result
                job_id = manager.submit(str(pdf_file), "markdown", enable_mineru_fallback=True)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.artifact == mineru_content
        assert job.java_artifact == java_content
        assert job.mineru_artifact == mineru_content
        assert job.mineru_json == mineru_json
        assert job.fallback_source == "mineru"

    def test_submit_mineru_fallback_pass(self, manager, pdf_file):
        java_content = "# Java output"

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text(java_content, encoding="utf-8")
            for job in manager._jobs.values():
                job.triage_decision = "PASS"

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner_cls:
                job_id = manager.submit(str(pdf_file), "markdown", enable_mineru_fallback=True)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.artifact == java_content
        assert job.java_artifact is None
        assert job.fallback_source is None
        mock_runner_cls.return_value.run.assert_not_called()

    def test_submit_mineru_fallback_disabled(self, manager, pdf_file):
        java_content = "# Java output"

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text(java_content, encoding="utf-8")
            for job in manager._jobs.values():
                job.triage_decision = "HARD_FAIL"

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner_cls:
                job_id = manager.submit(str(pdf_file), "markdown", enable_mineru_fallback=False)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.artifact == java_content
        assert job.java_artifact is None
        mock_runner_cls.return_value.run.assert_not_called()

    def test_submit_mineru_fallback_error(self, manager, pdf_file):
        java_content = "# Java output"

        from opendataloader_pdf_mcp.mineru import MinerUError

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text(java_content, encoding="utf-8")
            for job in manager._jobs.values():
                job.triage_decision = "HARD_FAIL"

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner_cls:
                mock_runner_cls.return_value.run.side_effect = MinerUError("mineru not found in PATH")
                job_id = manager.submit(str(pdf_file), "markdown", enable_mineru_fallback=True)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.FAILED
        assert job.java_artifact == java_content
        assert "mineru not found" in job.error

    def test_submit_mineru_cancelled_before_fallback(self, manager, pdf_file):
        java_content = "# Java output"

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text(java_content, encoding="utf-8")
            for job in manager._jobs.values():
                job.triage_decision = "HARD_FAIL"
                job._cancel_event.set()

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner_cls:
                job_id = manager.submit(str(pdf_file), "markdown", enable_mineru_fallback=True)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED, JobStatus.CANCELLED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.CANCELLED
        mock_runner_cls.return_value.run.assert_not_called()
```

- [x] **Step 2: Run tests to confirm they fail**

```bash
.venv/bin/python -m pytest tests/test_jobs.py::TestMinerUFallback -v
```

Expected: 5 failures — `AttributeError: 'Job' object has no attribute 'java_artifact'`

- [x] **Step 3: Add 4 new fields to the `Job` dataclass**

In `src/opendataloader_pdf_mcp/jobs.py`, find the `Job` dataclass and add four fields after `completed_at`:

```python
    # existing fields above ...
    completed_at:    str | None       = None
    mineru_artifact: str | None       = None
    mineru_json:     str | None       = None
    java_artifact:   str | None       = None
    fallback_source: str | None       = None
    _cancel_event:   threading.Event  = field(default_factory=threading.Event, repr=False, compare=False)
    _status_lock:    threading.Lock   = field(default_factory=threading.Lock, repr=False, compare=False)
```

The full updated `Job` dataclass (replace the existing one entirely):

```python
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
    mineru_artifact: str | None       = None
    mineru_json:     str | None       = None
    java_artifact:   str | None       = None
    fallback_source: str | None       = None
    _cancel_event:   threading.Event  = field(default_factory=threading.Event, repr=False, compare=False)
    _status_lock:    threading.Lock   = field(default_factory=threading.Lock, repr=False, compare=False)
```

- [x] **Step 4: Add `MinerURunner` import and `enable_mineru_fallback` to `submit()`**

At the top of `jobs.py`, add the import after the existing imports:

```python
from opendataloader_pdf_mcp.mineru import MinerUError, MinerURunner
```

The `submit()` signature already accepts `**kwargs` so `enable_mineru_fallback` flows through automatically — no signature change needed. It arrives in `job.kwargs` and is read in `_run()`.

- [x] **Step 5: Add the fallback branch to `_run()`**

Replace the entire `_run()` method in `jobs.py`:

```python
    def _run(self, job: Job, input_file: Path) -> None:
        with job._status_lock:
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
                    **{k: v for k, v in job.kwargs.items() if k != "enable_mineru_fallback"},
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

            with job._status_lock:
                if not job._cancel_event.is_set():
                    job.status = JobStatus.DONE
                    job.completed_at = _now()
                    with self._lock:
                        self._hash_index[job.content_hash] = job.job_id

        except Exception as exc:
            with job._status_lock:
                if job.status != JobStatus.CANCELLED:
                    job.error = str(exc)
                    job.status = JobStatus.FAILED
                    job.completed_at = _now()
            return

        # MinerU fallback: only when Java completed successfully with HARD_FAIL triage
        if (
            job.triage_decision == "HARD_FAIL"
            and job.kwargs.get("enable_mineru_fallback")
        ):
            if job._cancel_event.is_set():
                with job._status_lock:
                    job.status = JobStatus.CANCELLED
                    job.completed_at = _now()
                return

            job.java_artifact = job.artifact
            job.fallback_source = "mineru"
            with job._status_lock:
                job.status = JobStatus.RUNNING

            try:
                with tempfile.TemporaryDirectory() as mineru_tmp:
                    result = MinerURunner().run(input_file, Path(mineru_tmp))
                    job.mineru_artifact = result.markdown
                    job.mineru_json = result.json_str
                    job.artifact = result.markdown
                with job._status_lock:
                    job.status = JobStatus.DONE
                    job.completed_at = _now()
            except MinerUError as exc:
                with job._status_lock:
                    job.error = str(exc)
                    job.status = JobStatus.FAILED
                    job.completed_at = _now()
```

- [x] **Step 6: Run all job tests to confirm they pass**

```bash
.venv/bin/python -m pytest tests/test_jobs.py -v
```

Expected: all tests pass (existing 16 + new 5 = 21 total)

- [x] **Step 7: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py \
        python/opendataloader-pdf-mcp/tests/test_jobs.py
git commit -m "feat(mcp): add MinerU fallback branch to JobManager with tests"
```

> **Note (code-review hardening):** A follow-up commit `fix(mcp): harden MinerU fallback branch thread safety and dedup` was applied after code review:
> - Cancel-event check inside `_status_lock` (TOCTOU fix)
> - `_hash_index` updated after MinerU DONE (dedup gap fix)
> - `except Exception` instead of `except MinerUError` (catches all failure modes)
> - Test: removed dead `or True` guard; added `assert job.completed_at is not None`
> - **Actual test count: 18 passed** (not 21 — 13 pre-existing + 5 new)

---

## Task 3: Extend `server.py` — `submit_pdf` + `get_artifact`

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`
- Modify: `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py`

All commands run from `python/opendataloader-pdf-mcp/`.

- [x] **Step 1: Write the four failing tests**

Append a new `TestGetArtifactSource` class to `tests/test_server_async_tools.py`:

```python
class TestGetArtifactSource:
    def test_get_artifact_source_primary(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Primary"
                job.java_artifact = "# Java"
        result = get_artifact(job_id=job_id, source="primary")
        assert result == "# Primary"

    def test_get_artifact_source_java(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Primary"
                job.java_artifact = "# Java"
        result = get_artifact(job_id=job_id, source="java")
        assert result == "# Java"

    def test_get_artifact_source_mineru_json(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Primary"
                job.mineru_json = '{"pages": []}'
        result = get_artifact(job_id=job_id, source="mineru-json")
        assert result == '{"pages": []}'

    def test_get_artifact_invalid_source(self, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            job_id = submit_pdf(input_path=str(pdf_file))["job_id"]
            job = _job_manager.get(job_id)
            with job._status_lock:
                job.status = JobStatus.DONE
                job.artifact = "# Primary"
        result = get_artifact(job_id=job_id, source="bogus")
        assert "unknown source" in result.lower()
```

- [x] **Step 2: Run tests to confirm they fail**

```bash
.venv/bin/python -m pytest tests/test_server_async_tools.py::TestGetArtifactSource -v
```

Expected: 4 failures — `TypeError: get_artifact() got an unexpected keyword argument 'source'`

- [x] **Step 3: Add `enable_mineru_fallback` to `submit_pdf` and update `_collect_kwargs`**

In `server.py`, update the `_collect_kwargs` function signature to accept the new param (add at the end before `fmt`):

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
    enable_mineru_fallback: bool = False,
) -> dict:
    kwargs: dict = {}
    # ... (keep all existing body unchanged) ...
    if enable_mineru_fallback:
        kwargs["enable_mineru_fallback"] = True
    return kwargs
```

Update `submit_pdf` to accept and forward the new param:

```python
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
    enable_mineru_fallback: bool = False,
) -> dict:
    """Submit a PDF for async conversion. Returns a job_id to poll with get_job_status."""
    kwargs = _collect_kwargs(
        password, pages, keep_line_breaks, sanitize, content_safety_off,
        replace_invalid_chars, use_struct_tree, table_method, reading_order,
        markdown_page_separator, text_page_separator, html_page_separator,
        image_output, image_format, include_header_footer, detect_strikethrough,
        hybrid, hybrid_mode, hybrid_url, hybrid_timeout, hybrid_fallback,
        image_dir, format, enable_mineru_fallback,
    )
    job_id = _job_manager.submit(input_path, format, **kwargs)
    job = _job_manager.get(job_id)
    with job._status_lock:
        status = job.status
    if status == JobStatus.RUNNING:
        status = JobStatus.PENDING
    return {"job_id": job_id, "status": status.value, "content_hash": job.content_hash}
```

- [x] **Step 4: Update `get_artifact` to accept a `source` parameter**

Replace the existing `get_artifact` function in `server.py`:

```python
@mcp.tool()
def get_artifact(job_id: str, source: str = "primary") -> str:
    """Retrieve converted content for a completed job.

    Args:
        job_id: The job ID returned by submit_pdf.
        source: Which artifact to return. Values: primary (default), java, mineru-json.
    """
    job = _job_manager.get(job_id)
    if job.status == JobStatus.FAILED:
        raise RuntimeError(f"job {job_id} failed: {job.error}")
    if job.status == JobStatus.CANCELLED:
        raise RuntimeError(f"job {job_id} was cancelled")
    if job.status != JobStatus.DONE:
        raise RuntimeError(f"job {job_id} is {job.status.value}, not done")

    if source == "primary":
        return job.artifact
    if source == "java":
        return job.java_artifact
    if source == "mineru-json":
        return job.mineru_json
    return f"unknown source: {source!r}. Valid: primary, java, mineru-json"
```

- [x] **Step 5: Run all server tests**

```bash
.venv/bin/python -m pytest tests/test_server_async_tools.py -v
```

Expected: all tests pass (existing 12 + new 4 = 16 total) — **Actual: 20 passed** (16 pre-existing + 4 new)

- [x] **Step 6: Run the full test suite**

```bash
.venv/bin/python -m pytest tests/ -v
```

Expected: 40 existing + 4 (test_mineru) + 5 (test_jobs) + 4 (test_server) = 53 passed, 0 failed — **Actual: 55 passed, 0 failed**

- [x] **Step 7: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py \
        python/opendataloader-pdf-mcp/tests/test_server_async_tools.py
git commit -m "feat(mcp): add enable_mineru_fallback to submit_pdf and source param to get_artifact"
```

---

## Completion Status

**All tasks complete as of 2026-04-30.** Final state:

| Task | Commits | Tests |
|------|---------|-------|
| Task 1: MinerURunner | `feat(mcp): add MinerURunner subprocess wrapper with tests` + `fix(mcp): fix MinerU output path, add timeout, improve tests` | 4 passed |
| Task 2: Job fields + fallback branch | `feat(mcp): add MinerU fallback branch to JobManager with tests` + `fix(mcp): harden MinerU fallback branch thread safety and dedup` | 18 passed |
| Task 3: server.py extensions | `feat(mcp): add enable_mineru_fallback to submit_pdf and source param to get_artifact` | 20 passed |
| **Total** | **5 commits on `plan-c-mineru-fallback`** | **55 passed, 0 failed** |
