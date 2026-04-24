# MCP Ingestion v0 Design

## 1) Objective

Extend the existing `opendataloader-pdf-mcp` Python MCP server from a synchronous single-tool converter into an async job management layer. LLM agents can submit a PDF for conversion, poll status, retrieve the artifact when done, and cancel in-flight jobs — without blocking the MCP client while Java processes the file.

## 2) Constraints

- In-memory job store only — no persistence across server restarts.
- No new runtime dependencies beyond what `opendataloader-pdf-mcp` already declares.
- Existing `convert_pdf` synchronous tool remains unchanged and fully functional.
- Triage verdict (`AUTO_PASS / HUMAN_REVIEW / FAIL_REGENERATE`) and score are surfaced in job status as best-effort — populated if the Java pipeline exposes them through the Python wrapper, `null` otherwise.
- Python 3.10+, `unittest` style but project already uses pytest — keep pytest.

## 3) Architecture

```
python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/
├── server.py      (existing, extended — thin MCP registration layer)
└── jobs.py        (new — JobManager, Job dataclass, JobStatus enum)
```

`JobManager` has no MCP dependency and is independently testable.  
`server.py` imports `JobManager`, registers tools and resources, and delegates all logic to it.

## 4) Data Model

```python
class JobStatus(str, Enum):
    PENDING    = "pending"
    RUNNING    = "running"
    DONE       = "done"
    FAILED     = "failed"
    CANCELLED  = "cancelled"

@dataclass
class Job:
    job_id:          str           # UUID4
    content_hash:    str           # SHA-256 hex of input file bytes
    status:          JobStatus
    format:          str
    kwargs:          dict
    artifact:        str | None    # converted content, set on DONE
    triage_decision: str | None    # AUTO_PASS / HUMAN_REVIEW / FAIL_REGENERATE
    score:           float | None  # weighted quality score 0–100
    error:           str | None    # set on FAILED
    submitted_at:    str           # ISO-8601
    completed_at:    str | None    # ISO-8601, set on DONE/FAILED/CANCELLED
    _cancel_event:   threading.Event  # not serialised
```

## 5) JobManager API

```python
class JobManager:
    def submit(self, path: str, format: str, **kwargs) -> str:
        """Validate file exists and format is supported.
        Compute SHA-256. If hash in dedup index and existing job is DONE,
        return existing job_id. Otherwise create new Job, launch daemon
        thread, return new job_id."""

    def get(self, job_id: str) -> Job:
        """Return Job. Raise KeyError('unknown job_id: {id}') if not found."""

    def cancel(self, job_id: str) -> JobStatus:
        """Set cancel_event. If status is PENDING or RUNNING, transition to
        CANCELLED and set completed_at. If already terminal, no-op.
        Return current status."""
```

**Deduplication rule:** Only `DONE` jobs are deduplicated. A `FAILED` or `CANCELLED` job with the same hash gets a new job_id on resubmit.

**Background worker:**
1. Check `cancel_event` — if set, set status `CANCELLED`, return.
2. Set status `RUNNING`.
3. Write input to a `NamedTemporaryFile`, call `opendataloader_pdf.convert(...)` into a `TemporaryDirectory`.
4. Check `cancel_event` again — if set, set status `CANCELLED`, return.
5. Read output file into `job.artifact`.
6. Parse triage metadata from result if available; set `triage_decision` and `score`.
7. Set status `DONE`, set `completed_at`.
8. On any exception: set `job.error = str(e)`, set status `FAILED`, set `completed_at`.

## 6) MCP Tools (server.py additions)

### `submit_pdf`
Parameters: same signature as `convert_pdf` (input_path, format, all options).  
Returns: `{ "job_id": str, "status": "pending", "content_hash": str }`

### `get_job_status`
Parameters: `job_id: str`  
Returns: `{ "job_id", "status", "triage_decision", "score", "submitted_at", "completed_at" }`

### `cancel_job`
Parameters: `job_id: str`  
Returns: `{ "job_id", "status" }`  
Idempotent — cancelling a terminal job returns its current status without error.

### `get_artifact`
Parameters: `job_id: str`  
Returns: artifact string (same content as `convert_pdf` would have returned).  
Raises `RuntimeError` if job is not `DONE`.

## 7) MCP Resource

```
resource://jobs/{job_id}
```

- Served via a FastMCP URI template handler: `@mcp.resource("jobs://{job_id}")`. The handler is registered at server startup, not per-job.
- Returns artifact content as `text/plain` (or `application/json` when format is `json`).
- If job not found or not done: returns an error string (does not raise — MCP resource protocol requires a valid response).

## 8) Error Handling

| Scenario | Behaviour |
|---|---|
| `submit_pdf` — file not found | `FileNotFoundError` raised immediately, no job created |
| `submit_pdf` — unsupported format | `ValueError` raised immediately, no job created |
| `get_*` / `cancel_job` — unknown job_id | `KeyError: "unknown job_id: {id}"` |
| `get_artifact` — job not done | `RuntimeError("job {id} is {status}, not done")` |
| `get_artifact` — job failed | `RuntimeError("job {id} failed: {error}")` |
| `get_artifact` — job cancelled | `RuntimeError("job {id} was cancelled")` |
| Background thread exception | Caught; stored in `job.error`; status → `FAILED` |
| `cancel_job` on terminal job | No-op, returns current status |
| Resource read — job not done | Returns error text string, no raise |

## 9) Testing

**`tests/test_jobs.py`** — tests `JobManager` in isolation (no MCP):

| Test | Coverage |
|---|---|
| `test_submit_returns_job_id` | returns UUID string |
| `test_duplicate_hash_returns_same_job` | same file → same job_id (dedup on DONE) |
| `test_failed_job_not_deduped` | failed job → new job_id on resubmit |
| `test_get_unknown_job_raises` | `KeyError` on unknown id |
| `test_cancel_pending_job` | cancel before thread starts → `CANCELLED` |
| `test_cancel_idempotent` | cancel done job → no-op, returns `DONE` |
| `test_job_completes` | mock convert, assert status `DONE`, artifact populated |
| `test_job_failure` | mock convert raises, status `FAILED`, error field set |

**`tests/test_server_async_tools.py`** — tests the 4 new tools via direct calls:

| Test | Coverage |
|---|---|
| `test_submit_pdf_missing_file` | `FileNotFoundError` propagates |
| `test_get_status_unknown_id` | `KeyError` propagates |
| `test_get_artifact_not_done` | `RuntimeError` with status in message |
| `test_get_artifact_done` | returns artifact string |
| `test_cancel_job_tool` | status becomes `cancelled` |

## 10) Acceptance Criteria

- `submit_pdf` returns a job_id within 100ms regardless of PDF size.
- `get_job_status` reflects live status (`pending → running → done/failed/cancelled`).
- Same PDF submitted twice (within a session) returns the same job_id when the first job is `DONE`.
- `cancel_job` on a running job stops the worker before writing the artifact.
- `get_artifact` returns content identical to what `convert_pdf` would return for the same inputs.
- `resource://jobs/{job_id}` returns artifact content on capable MCP clients.
- All existing `convert_pdf` tests continue to pass.
- All new tests pass.

## 11) Out of Scope (v0)

- Persistence across server restarts (Plan B v1).
- Webhook / push notification on job completion.
- Job queue depth limits or backpressure.
- Real triage integration (depends on Java ProcessingResult surfacing through Python wrapper).
- MinerU fallback (Plan C).
