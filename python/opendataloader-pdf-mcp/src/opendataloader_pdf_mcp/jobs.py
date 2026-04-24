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
