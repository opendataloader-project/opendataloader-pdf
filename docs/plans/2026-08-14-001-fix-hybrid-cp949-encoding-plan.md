---
title: "fix: hybrid server fails to start on Korean-locale Windows (cp949 decode error)"
type: fix
status: implementation-ready
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
product_contract_source: ce-plan-bootstrap
created: 2026-08-14
origin: "https://github.com/opendataloader-project/opendataloader-pdf/issues/673"
---

# fix: hybrid server fails to start on Korean-locale Windows (cp949 decode error)

## Summary

On Windows machines with a Korean system locale (codepage `cp949`), the hybrid
backend (`opendataloader-pdf-hybrid`) fails the first conversion request with
`RuntimeError: ... 'cp949' codec can't decode byte 0xe2 ... illegal multibyte
sequence`. The failure originates inside `docling`'s model-loading code, which
opens a UTF-8 model config file without an explicit `encoding=`, so Python
falls back to the OS codepage. We cannot edit `docling`'s source (third-party
dependency), but we can force the whole interpreter into PEP 540 UTF-8 mode
before `docling` (or anything else) is imported, which makes every `open()`
call without an explicit encoding default to UTF-8 regardless of locale. The
reported workaround (`PYTHONUTF8=1` set *before* the process starts) confirms
this is the right lever — env vars read at interpreter startup can't be set
from inside the same running process, so the fix re-execs the process once
with `-X utf8` when it detects UTF-8 mode isn't already active.

**Product Contract preservation:** N/A — no upstream requirements document;
this plan originates directly from the GitHub issue (`ce-plan-bootstrap`).

---

## Problem Frame

**Reported behavior (Issue [#673](https://github.com/opendataloader-project/opendataloader-pdf/issues/673)):**

- Environment: Windows 11, Korean locale (system codepage `cp949`), Python
  3.12.10, `opendataloader-pdf[hybrid]` from PyPI, CPU-only.
- Repro: `opendataloader-pdf-hybrid --port 5002`, then convert any PDF with
  `--hybrid docling-fast` → the backend returns HTTP 500.
- Underlying error, raised from
  `docling/models/inference_engines/object_detection/transformers_engine.py`
  (`initialize()`):
  ```
  RuntimeError: Failed to load model from ...\models--docling-project--docling-layout-heron\snapshots\<hash>:
  'cp949' codec can't decode byte 0xe2 in position 616: illegal multibyte sequence
  ```
- Confirmed workaround: setting `PYTHONUTF8=1` in the environment before
  launching the server makes the model load succeed.

**Root cause:** `docling` opens its bundled model config with the platform
default text encoding (no `encoding="utf-8"` argument). On a `cp949`-locale
Windows console, that default is `cp949`, and the UTF-8 config file contains
byte sequences `cp949` cannot decode. This is a `docling` bug, but this repo
ships and packages the hybrid backend that triggers it, and Korean-locale
Windows is an explicit target audience (this repo already carries a prior fix,
commit `58a6dc7`, for a *different* cp949 crash — ASCII-only `--help` text).

**Scope of this fix:** neutralize the locale-dependent default encoding for
the whole `opendataloader-pdf-hybrid` process, at the earliest point in our
own code, before `docling` (or any other dependency) is imported. This is a
process-level, not a library-level, fix — appropriate since we do not control
`docling`'s source.

---

## Requirements

- **R1**: When `opendataloader-pdf-hybrid` starts on a system whose default
  text encoding is not UTF-8 (e.g., `cp949`-locale Windows) and the process
  was not already launched in Python UTF-8 mode, the process must re-launch
  itself with UTF-8 mode enabled before any dependency (including `docling`)
  is imported, so all subsequent `open()` calls without an explicit encoding
  default to UTF-8.
- **R2**: When the process is already running in UTF-8 mode (already
  `PYTHONUTF8=1`, already `-X utf8`, or on a platform whose default encoding
  is already UTF-8), startup must not re-exec — no duplicate process launch,
  no behavior change, no added startup latency.
- **R3**: The re-exec must preserve all original CLI arguments (`--port`,
  `--host`, `--ocr-lang`, etc.) so existing usage documented in the module
  docstring keeps working unchanged.
- **R4**: The fix must not require the user to set any environment variable
  manually — the documented `PYTHONUTF8=1` workaround becomes unnecessary
  after this fix, though it remains harmless if still set.

---

## Key Technical Decisions

**KTD1 — Fix at the process level (re-exec into UTF-8 mode), not by patching `docling`.**
`docling` is a third-party dependency (`docling[easyocr]>=2.91.0` in
`pyproject.toml`); this repo cannot durably fix its source, and pinning a
patched fork is out of proportion for a one-line encoding default. PEP 540
UTF-8 mode changes the *default* text encoding used by `open()` (and
everything downstream, including `docling`'s config load) without touching
`docling` at all. This mirrors the issue's own confirmed workaround
(`PYTHONUTF8=1`), just applied automatically instead of manually.
*Alternative rejected:* monkey-patching `io.open`/`builtins.open` process-wide
to force `encoding="utf-8"` — more invasive, harder to reason about, and risks
masking encoding bugs in *other* dependencies in surprising ways. UTF-8 mode
is the standard, documented mechanism for exactly this problem (PEP 540).

**KTD2 — Re-exec via `os.execv(sys.executable, [..., "-X", "utf8", "-m", "opendataloader_pdf.hybrid_server", ...sys.argv[1:]])`, not by setting `os.environ["PYTHONUTF8"]` alone.**
`PYTHONUTF8` (like `-X utf8`) is only read by the interpreter at startup;
setting it on `os.environ` after the interpreter has already started has no
effect on the running process's UTF-8 mode. The module already supports
`python -m opendataloader_pdf.hybrid_server` (`if __name__ == "__main__":
main()` at the bottom of the file), so re-invoking via `-m` is safe and needs
no new entry point. `sys.executable` is the running interpreter, so the
re-exec targets the exact same Python regardless of how the original process
was launched (console-script wrapper or `-m`).
*Alternative rejected:* documenting the workaround instead of fixing it —
rejected per the issue itself, which asks for an automatic fix, not a doc
note, since Korean-locale Windows users should not need to know Python
internals to run the tool.

**KTD3 — Guard the re-exec with `sys.flags.utf8_mode`, not a custom env-var sentinel.**
`sys.flags.utf8_mode` is `1` whenever the interpreter is already running in
UTF-8 mode (via `-X utf8`, `PYTHONUTF8=1`, or a platform where UTF-8 mode is
already the implied default). Checking this flag first is sufficient to avoid
a re-exec loop: after the one re-exec (which passes `-X utf8` explicitly),
the child process's `sys.flags.utf8_mode` is `1`, so the guard short-circuits
and no second re-exec happens. No extra environment-variable bookkeeping is
needed.

---

## Implementation Units

### U1. Add a UTF-8-mode re-exec guard to the hybrid server entry point

**Goal:** Ensure `opendataloader-pdf-hybrid` always runs with Python UTF-8
mode active before any dependency import that could be sensitive to the
platform's default text encoding.

**Requirements:** R1, R2, R3, R4

**Dependencies:** none

**Files:**
- `python/opendataloader-pdf/src/opendataloader_pdf/hybrid_server.py` (add helper function + call it first in `main()`)
- `python/opendataloader-pdf/tests/test_hybrid_server_utf8_reexec.py` (new test file)

**Approach:**
1. Add a module-level helper, e.g. `_reexec_with_utf8_if_needed()`, placed
   near the top of `hybrid_server.py` (after imports, before `_check_dependencies`).
2. Guard: if `sys.flags.utf8_mode` is already truthy, return immediately —
   no-op (R2).
3. Otherwise, re-exec: `os.execv(sys.executable, [sys.executable, "-X", "utf8", "-m", "opendataloader_pdf.hybrid_server"] + sys.argv[1:])`.
   This replaces the current process (on POSIX, a true `exec`; on Windows,
   CPython's `os.execv` spawns the child and exits the parent with the
   child's exit code — either way the caller observes one continuous
   process). Preserves original argv (R3).
4. Call `_reexec_with_utf8_if_needed()` as the **first statement** inside
   `main()`, before `_check_dependencies()` and before any other logic —
   this is what guarantees `docling`'s later import/model-load happens only
   after UTF-8 mode is active.
5. Do not change `_check_dependencies()`, argument parsing, or any request
   handling — this unit is purely an early-startup gate.

**Patterns to follow:** the existing ASCII-only-help-text fix (commit
`58a6dc7`, `fix(hybrid): replace em-dash in --help text crashing on cp949
consoles`) is the direct prior-art for "Korean-locale Windows cp949" fixes in
this exact file — same problem family, same file, same audience. Follow its
commit-message shape (`Objective` / `Approach` / `Evidence`) for consistency,
even though the code technique here (UTF-8 mode re-exec) differs from that
fix's ASCII-substitution technique.

**Test scenarios:**
- `sys.flags.utf8_mode` already `1` (e.g., simulate via `monkeypatch.setattr(sys.flags, ...)` is not possible since `sys.flags` is read-only — instead patch the helper's read of the flag, or run the check function against a small wrapper that takes the flag as a parameter for testability) → `os.execv` is NOT called.
- `sys.flags.utf8_mode` falsy → `os.execv` IS called exactly once, with `sys.executable` as the program, and an argv list that contains `"-X"`, `"utf8"`, `"-m"`, `"opendataloader_pdf.hybrid_server"`, followed by the original `sys.argv[1:]` (test with a representative arg list, e.g. `["--port", "5002", "--ocr-lang", "ko"]`).
- Original CLI arguments are passed through unchanged and in order (covers R3) — assert the tail of the constructed argv exactly equals `sys.argv[1:]`.
- `main()` calls the re-exec guard before `_check_dependencies()` — verify via call-order assertion (e.g., patch both and assert the guard's mock was invoked first, or patch the guard to raise and assert `_check_dependencies` is never reached before it in that code path only when the guard decides to re-exec — since in the no-op case both still run, assert ordering by call sequence, not by short-circuit).
- Test expectation: none for the "no-op" path beyond the assertion above — no behavioral change to server startup, argument parsing, or request handling when UTF-8 mode is already active.

**Verification:** All new and existing tests in
`python/opendataloader-pdf/tests/` pass. Manually confirm intent by reasoning
through the sequence (no Windows/cp949 machine available in this environment):
with the fix, any process without UTF-8 mode active re-execs into `-X utf8`
before `docling` is imported, so `docling`'s later `open()` calls default to
UTF-8 regardless of locale — directly closing the reported error.

---

## Testability Note (Execution Note)

This bug is Windows/`cp949`-locale-specific and cannot be reproduced exactly
in this Linux development environment. Verification here is necessarily at
the level of: (a) unit tests proving the guard's decision logic and argv
construction are correct, and (b) reasoning that PEP 540 UTF-8 mode is
documented to make `open()` (used internally by `docling`, without an
explicit `encoding=`) default to UTF-8 regardless of the OS locale — which is
exactly the mechanism the issue's own reported workaround (`PYTHONUTF8=1`)
relies on. Do not claim a live Windows repro; the plan-level and PR-level
evidence should say this explicitly rather than overclaiming a hands-on
Windows verification that didn't happen.

---

## Scope Boundaries

**In scope:** the hybrid server's own process startup (`hybrid_server.py:main`).

**Out of scope / deferred:**
- Fixing the encoding bug inside `docling` itself (upstream, third-party;
  not our source to change).
- Any change to the Java CLI, the Node/Python thin wrappers around the Java
  binary, or non-hybrid conversion paths — none of those import `docling` or
  are affected by this locale issue.
- Broader "make the whole CLI locale-proof" effort — this fix is scoped to
  the one reported failure mode (hybrid server startup / first model load).

---

## Risks & Dependencies

- **Low risk.** The change only affects process startup for the
  `opendataloader-pdf-hybrid` entry point, and only takes the re-exec branch
  when UTF-8 mode is not already active (the common case on Linux/macOS CI is
  already UTF-8, so CI should exercise the no-op path; the re-exec path is
  Windows/legacy-locale-specific and cannot be exercised on this project's
  Linux CI, hence the emphasis on unit-testing the decision logic directly).
- **Windows-specific `os.execv` semantics**: CPython on Windows implements
  `os.execv` by spawning a child and exiting the parent with its return code,
  rather than a true in-place exec. This is a well-established, documented
  behavior (used by other Python tools for the same "re-launch with an -X
  flag" pattern) and preserves stdin/stdout/stderr and exit code transparently
  for a foreground CLI server process.

---

## Definition of Done

- [ ] `_reexec_with_utf8_if_needed()` (or equivalently named helper) added to
      `hybrid_server.py`, called as the first statement in `main()`.
- [ ] New unit tests in `test_hybrid_server_utf8_reexec.py` cover: no-op when
      UTF-8 mode already active; re-exec triggered with correct `sys.executable`,
      `-X utf8`, `-m opendataloader_pdf.hybrid_server`, and preserved argv when not.
- [ ] Full existing Python test suite (`python/opendataloader-pdf/tests/`)
      still passes.
- [ ] PR description follows this repo's commit/PR conventions (see
      `CONTRIBUTING.md` and prior-art commit `58a6dc7`), references issue #673,
      and is honest about the lack of a live Windows/cp949 repro environment.
