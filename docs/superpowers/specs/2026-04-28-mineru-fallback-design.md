# MinerU Fallback — Design Spec

**Date:** 2026-04-28
**Branch:** plan-c-mineru-fallback
**Status:** Approved

---

## 1. Problem

The Java extraction pipeline produces a `TriageDecision` of `HARD_FAIL` for documents
where extraction quality is unacceptably poor (e.g., scanned PDFs with no text layer,
highly complex layouts). Currently the job simply returns the low-quality Java output
with no recovery path. MinerU is a Python-based PDF extraction tool that handles these
hard cases better. Plan C wires MinerU as an opt-in fallback that runs automatically
when Java hard-fails.

---

## 2. Scope

- **In scope:** MinerU invoked as a local subprocess fallback within the async MCP job layer.
  Output formats: markdown (primary) + JSON. Opt-in per job via `enable_mineru_fallback=True`.
  Java output preserved in a separate field for caller inspection.
- **Out of scope:** Remote MinerU service, merging MinerU + Java output at page granularity,
  fallback on SOFT_FAIL, any Java-layer changes.

---

## 3. Architecture

### 3.1 New Components

**`mineru.py`** — `MinerURunner` + `MinerUResult` + `MinerUError`

```
MinerUResult
  markdown:  str
  json_str:  str
  exit_code: int
  stderr:    str

MinerUError(Exception)

MinerURunner
  run(pdf_path: Path, output_dir: Path) -> MinerUResult
```

`MinerURunner.run()`:
1. `shutil.which("mineru")` — raises `MinerUError("mineru not found in PATH")` if absent.
2. `subprocess.run(["mineru", ...], capture_output=True, text=True)` in `output_dir`.
3. Non-zero exit → `MinerUError(f"mineru exited {rc}: {stderr[:500]}")`.
4. Read `{stem}.md` and `{stem}.json` from `output_dir`. Missing → `MinerUError("mineru produced no output")`.
5. Return `MinerUResult`.

### 3.2 Modified: `Job` dataclass

Three new fields added to the existing `Job` dataclass in `jobs.py`:

```python
mineru_artifact:  str | None = None   # MinerU markdown output
mineru_json:      str | None = None   # MinerU JSON output
java_artifact:    str | None = None   # original Java output (moved here on HARD_FAIL)
fallback_source:  str | None = None   # "mineru" when fallback ran, else None
```

The existing `artifact` field remains the primary surface:
- Fallback ran: `artifact = MinerU markdown`
- Fallback did not run: `artifact = Java output` (no change to existing behaviour)

### 3.3 Modified: `JobManager`

**`submit()`**: accepts `enable_mineru_fallback: bool = False`, stored in `job.kwargs`.

**`_run()`**: after Java extraction completes successfully (status would be DONE):

```
if triage_decision == "HARD_FAIL" and kwargs.get("enable_mineru_fallback"):
    if _cancel_event.is_set():
        → CANCELLED (no MinerU call)
    move artifact → java_artifact
    fallback_source = "mineru"
    status = RUNNING
    try:
        result = MinerURunner().run(input_file, tmp_dir)
        mineru_artifact = result.markdown
        mineru_json     = result.json_str
        artifact        = result.markdown
        status = DONE
    except MinerUError as e:
        error  = str(e)
        status = FAILED
        # java_artifact still set — caller can use it
```

Status path (fallback): `PENDING → RUNNING → DONE → RUNNING → DONE`
Status path (MinerU error): `PENDING → RUNNING → DONE → RUNNING → FAILED`

All status transitions use `job._status_lock` (existing pattern).

### 3.4 Modified: `server.py`

**`submit_pdf` tool**: new parameter `enable_mineru_fallback: bool = False`, forwarded to `JobManager.submit()`.

**`get_artifact` tool**: new parameter `source: str = "primary"`:

| `source` value | Returns |
|---|---|
| `"primary"` | `job.artifact` (default — no breaking change) |
| `"java"` | `job.java_artifact` |
| `"mineru-json"` | `job.mineru_json` |
| anything else | error string `"unknown source: {value}"` |

---

## 4. Error Handling

| Scenario | Behaviour |
|---|---|
| `mineru` not in PATH | `MinerUError` → job `FAILED`, `java_artifact` set |
| MinerU non-zero exit | `MinerUError` with stderr → job `FAILED`, `java_artifact` set |
| MinerU output files absent | `MinerUError` → job `FAILED`, `java_artifact` set |
| Cancel before fallback | `CANCELLED`, MinerU not called |
| Java itself failed (no DONE) | Fallback not attempted; existing FAILED state unchanged |
| `enable_mineru_fallback=False` | Fallback never triggered regardless of triage result |

---

## 5. Testing

### `test_mineru.py` (new)

| Test | What it verifies |
|---|---|
| `test_run_success` | Mocked subprocess + files → `MinerUResult` populated |
| `test_run_not_in_path` | `shutil.which=None` → `MinerUError` |
| `test_run_nonzero_exit` | exit 1 → `MinerUError` with stderr |
| `test_run_missing_output` | exit 0, no files → `MinerUError` |

### `test_jobs.py` additions

| Test | What it verifies |
|---|---|
| `test_submit_mineru_fallback_hard_fail` | HARD_FAIL + flag → `artifact=mineru_md`, `java_artifact=java_md`, `fallback_source="mineru"`, DONE |
| `test_submit_mineru_fallback_pass` | PASS + flag → MinerU not called, `java_artifact=None` |
| `test_submit_mineru_fallback_disabled` | HARD_FAIL, flag=False → MinerU not called |
| `test_submit_mineru_fallback_error` | MinerURunner raises → FAILED, `java_artifact` still set |
| `test_submit_mineru_cancelled_before_fallback` | Cancel event set → CANCELLED, MinerU not called |

### `test_server_async_tools.py` additions

| Test | What it verifies |
|---|---|
| `test_get_artifact_source_primary` | returns `job.artifact` |
| `test_get_artifact_source_java` | returns `job.java_artifact` |
| `test_get_artifact_source_mineru_json` | returns `job.mineru_json` |
| `test_get_artifact_invalid_source` | returns error string |

---

## 6. Files Changed

| File | Change |
|---|---|
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/mineru.py` | New — `MinerURunner`, `MinerUResult`, `MinerUError` |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py` | 4 new `Job` fields, fallback branch in `_run()`, `enable_mineru_fallback` in `submit()` |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py` | `submit_pdf` new param, `get_artifact` new `source` param |
| `python/opendataloader-pdf-mcp/tests/test_mineru.py` | New — 4 unit tests |
| `python/opendataloader-pdf-mcp/tests/test_jobs.py` | 5 new tests |
| `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py` | 4 new tests |

No Java changes. No new dependencies.
