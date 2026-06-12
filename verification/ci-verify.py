#!/usr/bin/env python3
"""
CI verification script for opendataloader-pdf CLI options.
Three-level verification: Smoke / Content assertion / Comparison.
"""

import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile

# Force stdout/stderr to UTF-8 so this script does not crash when JAR output
# contains characters outside the system locale (e.g. cp949 on Korean Windows).
# Linux CI runners use UTF-8 by default; this is a no-op there.
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except AttributeError:
        pass

# ---------------------------------------------------------------------------
# Repository root (two levels up from this file: verification/ci-verify.py)
# ---------------------------------------------------------------------------
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------------------
# PDF Fixtures
# ---------------------------------------------------------------------------
PDF_BASIC      = os.path.join(REPO_ROOT, "samples/pdf/lorem.pdf")
PDF_MULTIPAGE  = os.path.join(REPO_ROOT, "samples/pdf/2408.02509v1.pdf")
PDF_IMAGES     = os.path.join(REPO_ROOT, "samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-06_Brochure.pdf")
PDF_TABLES     = os.path.join(REPO_ROOT, "samples/pdf/issue-336-conto-economico-bialetti.pdf")
PDF_STRUCTURED = os.path.join(REPO_ROOT, "samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-08_BookChapter.pdf")
PDF_PASSWORD      = os.path.join(REPO_ROOT, "samples/pdf/password-protected.pdf")
PDF_STRIKETHROUGH = os.path.join(REPO_ROOT, "samples/pdf/strikethrough.pdf")
# strikethrough.pdf carries a /StrikeOut markup annotation, which the current
# line-based detection does not see. Native annotation support is in progress
# upstream (veraPDF isStrikethroughText property):
# https://github.com/opendataloader-project/opendataloader-pdf-tasks/issues/348
# Flip to False to re-enable the check once that lands.
STRIKETHROUGH_KNOWN_ISSUE = True
PDF_INVALID_CHARS = os.path.join(REPO_ROOT, "samples/pdf/invalid-chars.pdf")
PDF_SANITIZE      = os.path.join(REPO_ROOT, "samples/pdf/sanitize-target.pdf")
PDF_SAFETY        = os.path.join(REPO_ROOT, "samples/pdf/safety-trigger.pdf")
PDF_FAKE_JPG      = os.path.join(REPO_ROOT, "samples/pdf/fake-jpg.pdf")
PDF_EMPTY         = os.path.join(REPO_ROOT, "samples/pdf/empty.pdf")
PNG_FILE          = os.path.join(REPO_ROOT, "samples/pdf/abcd.png")
MIXED_DIR         = os.path.join(REPO_ROOT, "samples/pdf/mixed")
PDF_SANITIZATION_TARGETS = os.path.join(REPO_ROOT, "samples/pdf/sanitization-targets.pdf")
PASSWORD = "test"

# ---------------------------------------------------------------------------
# Stack-trace leakage markers — must NEVER appear in user-facing output.
# When the CLI catches a Java exception (bad PDF, unreadable file, etc.) it
# is expected to surface a friendly one-line error, not a raw veraPDF or
# java.io stack trace.
# ---------------------------------------------------------------------------
STACK_TRACE_LEAK_MARKERS = (
    "org.verapdf",
    "java.io.IOException:",
    "\tat ",
)

# ---------------------------------------------------------------------------
# Option coverage registries
# ---------------------------------------------------------------------------
COVERED_OPTIONS = {
    "--format", "--output-dir", "--quiet", "--to-stdout",
    "--password", "--keep-line-breaks", "--replace-invalid-chars",
    "--pages", "--include-header-footer", "--detect-strikethrough",
    "--sanitize", "--content-safety-off", "--use-struct-tree",
    "--table-method", "--reading-order", "--threads",
    "--image-output", "--image-format", "--image-dir",
    "--markdown-with-html",
    "--markdown-page-separator", "--text-page-separator", "--html-page-separator",
}

HYBRID_OPTIONS = {
    "--hybrid", "--hybrid-mode", "--hybrid-timeout",
    "--hybrid-fallback", "--hybrid-url",
    "--hybrid-hancom-ai-regionlist-strategy",
    "--hybrid-hancom-ai-ocr-strategy",
    "--hybrid-hancom-ai-image-cache",
}

# ---------------------------------------------------------------------------
# Global results accumulator
# ---------------------------------------------------------------------------
RESULTS: list[tuple[str, bool | None]] = []


# ---------------------------------------------------------------------------
# Core helpers
# ---------------------------------------------------------------------------

def run_cli(
    args: list[str], capture_output: bool = True, timeout: int = 120
) -> subprocess.CompletedProcess:
    """Run the opendataloader-pdf CLI with given args using sys.executable for portability.

    Forces UTF-8 decoding with errors=replace so the runner does not crash on
    non-ASCII output under non-UTF-8 system locales (e.g. cp949 on Korean
    Windows), where the default text=True decode would set stdout/stderr to
    None when the JAR emits a byte the locale cannot represent.

    A timeout bounds each invocation so a stuck CLI raises TimeoutExpired
    instead of hanging the whole verification job indefinitely.
    """
    cmd = [sys.executable, "-m", "opendataloader_pdf"] + args
    return subprocess.run(
        cmd,
        capture_output=capture_output,
        text=True,
        encoding="utf-8",
        errors="replace",
        cwd=REPO_ROOT,
        timeout=timeout,
    )


def record(label: str, passed: bool | None) -> None:
    """Record a test result and print it immediately."""
    RESULTS.append((label, passed))
    if passed is None:
        print(f"  SKIP | {label}")
    elif passed:
        print(f"  PASS | {label}")
    else:
        print(f"  FAIL | {label}")


# ---------------------------------------------------------------------------
# Level 1 – Smoke
# ---------------------------------------------------------------------------

def smoke(label: str, cmd_args: list[str], outdir: str) -> bool:
    """Level 1: exit code == 0 AND at least one file created in outdir."""
    try:
        result = run_cli(cmd_args)
        if result.returncode != 0:
            print(f"       [smoke] non-zero exit: {result.returncode}")
            print(f"       stderr: {result.stderr[:300]}")
            return False
        files = [f for f in os.listdir(outdir) if os.path.isfile(os.path.join(outdir, f))]
        if not files:
            print(f"       [smoke] no output files created in {outdir}")
            return False
        return True
    except Exception as exc:
        print(f"       [smoke] exception: {exc}")
        return False


# ---------------------------------------------------------------------------
# Level 2 – Content assertion
# ---------------------------------------------------------------------------

def assert_content(
    label: str,
    filepath: str,
    must_contain: list[str] | None = None,
    must_not_contain: list[str] | None = None,
    case_insensitive: bool = False,
) -> bool:
    """Level 2: check text file content for required/forbidden strings."""
    try:
        with open(filepath, "r", encoding="utf-8", errors="replace") as fh:
            content = fh.read()
        if case_insensitive:
            content_cmp = content.lower()
            must_contain_cmp = [s.lower() for s in (must_contain or [])]
            must_not_contain_cmp = [s.lower() for s in (must_not_contain or [])]
        else:
            content_cmp = content
            must_contain_cmp = list(must_contain or [])
            must_not_contain_cmp = list(must_not_contain or [])

        for needle in must_contain_cmp:
            if needle not in content_cmp:
                print(f"       [content] missing required string: {needle!r}")
                return False
        for needle in must_not_contain_cmp:
            if needle in content_cmp:
                print(f"       [content] found forbidden string: {needle!r}")
                return False
        # When no string assertions are supplied (None or empty list), fall back
        # to a non-empty check so an empty output file never passes vacuously.
        if not must_contain and not must_not_contain:
            if not content.strip():
                print(f"       [content] file is empty")
                return False
        return True
    except Exception as exc:
        print(f"       [content] exception: {exc}")
        return False


def assert_stderr_empty(label: str, cmd_args: list[str]) -> bool:
    """For --quiet: verify stderr is completely empty."""
    try:
        result = run_cli(cmd_args)
        if result.returncode != 0:
            print(f"       [stderr] non-zero exit: {result.returncode}")
            return False
        if result.stderr.strip():
            print(f"       [stderr] stderr not empty: {result.stderr[:200]!r}")
            return False
        return True
    except Exception as exc:
        print(f"       [stderr] exception: {exc}")
        return False


def assert_stdout_nonempty(label: str, cmd_args: list[str]) -> bool:
    """For --to-stdout: verify stdout is not empty."""
    try:
        result = run_cli(cmd_args)
        if result.returncode != 0:
            print(f"       [stdout] non-zero exit: {result.returncode}")
            return False
        if not result.stdout.strip():
            print(f"       [stdout] stdout is empty")
            return False
        return True
    except Exception as exc:
        print(f"       [stdout] exception: {exc}")
        return False


# ---------------------------------------------------------------------------
# Level 2.5 – File size assertion
# ---------------------------------------------------------------------------

def assert_file_size(
    label: str,
    filepath: str,
    smaller_than: int | None = None,
    larger_than: int | None = None,
) -> bool:
    """Level 2.5: compare file size against thresholds (bytes)."""
    try:
        size = os.path.getsize(filepath)
        if smaller_than is not None and size >= smaller_than:
            print(f"       [size] {size} >= {smaller_than} (expected smaller)")
            return False
        if larger_than is not None and size <= larger_than:
            print(f"       [size] {size} <= {larger_than} (expected larger)")
            return False
        return True
    except Exception as exc:
        print(f"       [size] exception: {exc}")
        return False


# ---------------------------------------------------------------------------
# Level 3 – Comparison
# ---------------------------------------------------------------------------

def _find_first_output_file(outdir: str) -> str | None:
    """Return the path of the first regular file found in outdir, or None."""
    for name in sorted(os.listdir(outdir)):
        path = os.path.join(outdir, name)
        if os.path.isfile(path):
            return path
    return None


def run_comparison(
    label: str,
    cmd_baseline: list[str],
    cmd_variant: list[str],
    outdir: str,
    expect: str = "differ",
) -> bool:
    """Level 3: compare baseline vs variant output. expect='differ'|'identical'."""
    try:
        with tempfile.TemporaryDirectory() as base_dir, tempfile.TemporaryDirectory() as var_dir:
            res_base = run_cli(cmd_baseline + ["--output-dir", base_dir])
            res_var  = run_cli(cmd_variant  + ["--output-dir", var_dir])

            if res_base.returncode != 0:
                print(f"       [compare] baseline non-zero exit: {res_base.returncode}")
                print(f"       stderr: {res_base.stderr[:300]}")
                return False
            if res_var.returncode != 0:
                print(f"       [compare] variant non-zero exit: {res_var.returncode}")
                print(f"       stderr: {res_var.stderr[:300]}")
                return False

            base_file = _find_first_output_file(base_dir)
            var_file  = _find_first_output_file(var_dir)

            if base_file is None or var_file is None:
                print(f"       [compare] missing output file (base={base_file}, var={var_file})")
                return False

            base_content = open(base_file, "rb").read()
            var_content  = open(var_file,  "rb").read()

            are_same = base_content == var_content
            if expect == "differ":
                if are_same:
                    print(f"       [compare] outputs are identical but expected to differ")
                    return False
                return True
            else:  # identical
                if not are_same:
                    print(f"       [compare] outputs differ but expected to be identical")
                    return False
                return True
    except Exception as exc:
        print(f"       [compare] exception: {exc}")
        return False


# ---------------------------------------------------------------------------
# Option coverage check
# ---------------------------------------------------------------------------

def check_option_coverage() -> None:
    """Read options.json and verify all non-hybrid options are in COVERED_OPTIONS."""
    options_path = os.path.join(REPO_ROOT, "options.json")
    if not os.path.exists(options_path):
        print(f"WARNING: options.json not found at {options_path}, skipping coverage check")
        return

    with open(options_path, "r", encoding="utf-8") as fh:
        data = json.load(fh)

    uncovered: list[str] = []
    for opt in data.get("options", []):
        flag = "--" + opt["name"]
        if flag in HYBRID_OPTIONS:
            continue
        if flag not in COVERED_OPTIONS:
            uncovered.append(flag)

    if uncovered:
        print("ERROR: The following options are not covered by COVERED_OPTIONS:")
        for flag in uncovered:
            print(f"  {flag}")
        sys.exit(1)
    else:
        print(f"Option coverage check passed ({len(COVERED_OPTIONS)} options covered).")


# ---------------------------------------------------------------------------
# Helper: find output file by extension in a directory
# ---------------------------------------------------------------------------

def _find_file_by_ext(directory: str, ext: str) -> str | None:
    """Return the first file with the given extension (e.g. '.md') in directory."""
    pattern = os.path.join(directory, f"*{ext}")
    matches = glob.glob(pattern)
    return matches[0] if matches else None


def _find_image_files(directory: str) -> list[str]:
    """Return all image files (.png, .jpg, .jpeg) in directory (recursive)."""
    results = []
    for root, _dirs, files in os.walk(directory):
        for name in files:
            if name.lower().endswith((".png", ".jpg", ".jpeg")):
                results.append(os.path.join(root, name))
    return results


# ---------------------------------------------------------------------------
# Encoding safety helpers
# ---------------------------------------------------------------------------

def _verify_help_cp949_safe(command: list[str]) -> bool | None:
    """Run `command --help` with PYTHONIOENCODING=cp949 and check for UnicodeEncodeError.

    Returns True (PASS), False (FAIL — UnicodeEncodeError detected), or None (SKIP).
    Korean Windows console uses cp949; non-ASCII chars (em-dash etc.) in help text
    crash argparse output when stdout codec cannot encode them.
    """
    env = os.environ.copy()
    env["PYTHONIOENCODING"] = "cp949"
    try:
        result = subprocess.run(
            command + ["--help"],
            env=env,
            capture_output=True,
            timeout=30,
        )
    except Exception as exc:
        print(f"       [cp949 help] exception: {exc}")
        return None

    stderr_text = result.stderr.decode("utf-8", errors="replace")
    stdout_text = result.stdout.decode("utf-8", errors="replace")
    if "UnicodeEncodeError" in stderr_text or "UnicodeEncodeError" in stdout_text:
        print("       [cp949 help] UnicodeEncodeError detected in --help output")
        return False
    if result.returncode != 0:
        print(f"       [cp949 help] non-zero exit: {result.returncode}")
        return False
    return True


def _verify_hybrid_help_cp949_safe() -> bool | None:
    """Test hybrid CLI --help under cp949. SKIP if [hybrid] extras not installed."""
    if shutil.which("opendataloader-pdf-hybrid") is None:
        print("       [cp949 help] hybrid CLI not installed, skipping")
        return None
    # Pre-check: does hybrid CLI bail out at dependency check?
    pre = subprocess.run(
        [sys.executable, "-c",
         "from opendataloader_pdf.hybrid_server import _check_dependencies; _check_dependencies()"],
        capture_output=True,
        timeout=15,
    )
    if pre.returncode != 0:
        stderr = pre.stderr.decode("utf-8", errors="replace")
        if "Missing dependencies" in stderr or "ImportError" in stderr:
            print("       [cp949 help] hybrid deps missing, skipping")
            return None
    return _verify_help_cp949_safe(
        [sys.executable, "-m", "opendataloader_pdf.hybrid_server"]
    )


# ---------------------------------------------------------------------------
# Hybrid mode behavior — fail-fast contract
#
# Hybrid mode delegates page processing to an external backend server. When
# any backend page is left unprocessed and the user did not opt in to Java
# fallback, the CLI must surface that as a non-zero exit code — otherwise
# automation (subprocess.run, CI exit-code branches, library wrappers) reads
# the silent partial result as success.
#
# Note on coverage scope: --hybrid-fallback applies only to chunk-level
# processing failures, not to the initial backend availability check (which
# is intentionally unconditional — see HybridDocumentProcessor Phase 0).
# Verifying the fallback contract therefore requires a reachable backend
# that emits chunk-level failures, which is out of scope for this runner
# (no mock backend infrastructure). The unreachable-URL path below exercises
# the fail-fast contract that callers depend on most.
# ---------------------------------------------------------------------------

# Reserved port that never accepts connections — used to simulate a
# guaranteed-unreachable hybrid backend.
UNREACHABLE_HYBRID_URL = "http://127.0.0.1:1"


def _verify_hybrid_fail_fast_on_unreachable_backend() -> bool | None:
    """
    Contract: when the hybrid backend is unreachable, the CLI must exit with
    a non-zero code. Anything else means backend failures are silently
    dropped, leaving callers unable to detect that processing did not
    actually happen.
    """
    if shutil.which("opendataloader-pdf-hybrid") is None:
        print("       [hybrid fail-fast] hybrid CLI not installed, skipping")
        return None
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_BASIC,
            "--hybrid", "docling-fast",
            "--hybrid-url", UNREACHABLE_HYBRID_URL,
            "--output-dir", tmpdir,
        ])
        if result.returncode == 0:
            files = [f for f in os.listdir(tmpdir)
                     if os.path.isfile(os.path.join(tmpdir, f))]
            print(f"       [hybrid fail-fast] expected non-zero exit, got 0 (files: {files})")
            print(f"       stderr: {result.stderr[:300]}")
            return False
        return True


# ---------------------------------------------------------------------------
# Stack-trace leakage helper
# ---------------------------------------------------------------------------

def _has_stack_trace_leak(text: str | None) -> str | None:
    """Return the first leak marker found in text, or None if clean / empty."""
    if not text:
        return None
    for marker in STACK_TRACE_LEAK_MARKERS:
        if marker in text:
            return marker
    return None


def _assert_no_leak(label: str, stdout: str | None, stderr: str | None) -> bool:
    """Verify neither stdout nor stderr leak a Java stack trace."""
    for stream_name, text in (("stdout", stdout), ("stderr", stderr)):
        leak = _has_stack_trace_leak(text)
        if leak is not None:
            print(f"       [{label}] stack-trace leak on {stream_name}: {leak!r}")
            return False
    return True


# ---------------------------------------------------------------------------
# Input validation (malformed and non-PDF inputs)
#
# These scenarios are separate from CLI option effects: they cover the
# input-rejection contract for malformed PDFs and non-PDF inputs. The core
# invariant is that NO stack trace ever reaches the user — the CLI must
# surface a one-line friendly error instead.
# ---------------------------------------------------------------------------

def verify_input_validation() -> None:
    print("\n--- input validation ---")

    # 1. .pdf-named JPEG content as top-level CLI argument.
    if not os.path.exists(PDF_FAKE_JPG):
        record("fake-jpg.pdf top-level [SKIPPED: fixture missing]", None)
    else:
        result = run_cli([PDF_FAKE_JPG])
        ok = result.returncode != 0
        if ok and "is not a valid PDF file" not in result.stdout \
                and "missing %PDF" not in result.stdout:
            print(f"       [fake-jpg] expected friendly error on stdout, got: "
                  f"{result.stdout[:200]!r}")
            ok = False
        if ok:
            ok = _assert_no_leak("fake-jpg", result.stdout, result.stderr)
        record("fake-jpg.pdf top-level → friendly error + no leak", ok)

    # 2. Zero-byte .pdf as top-level CLI argument.
    if not os.path.exists(PDF_EMPTY):
        record("empty.pdf top-level [SKIPPED: fixture missing]", None)
    else:
        result = run_cli([PDF_EMPTY])
        ok = result.returncode != 0
        if ok:
            ok = _assert_no_leak("empty.pdf", result.stdout, result.stderr)
        record("empty.pdf top-level → non-zero + no leak", ok)

    # 3. .png file as top-level CLI argument.
    if not os.path.exists(PNG_FILE):
        record("abcd.png top-level [SKIPPED: fixture missing]", None)
    else:
        result = run_cli([PNG_FILE])
        ok = result.returncode != 0
        if ok and "is not a PDF file" not in result.stdout \
                and "not a PDF" not in result.stdout:
            print(f"       [abcd.png] expected 'not a PDF' on stdout, got: "
                  f"{result.stdout[:200]!r}")
            ok = False
        if ok:
            ok = _assert_no_leak("abcd.png", result.stdout, result.stderr)
        record("abcd.png top-level → friendly error + no leak", ok)

    # 4. Folder containing one PDF + one PNG.
    # Contract: PDF processed, PNG silently skipped, exit 0, no stack trace.
    if not os.path.exists(MIXED_DIR):
        record("mixed/ folder [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as outdir:
            result = run_cli([MIXED_DIR, "--output-dir", outdir])
            ok = result.returncode == 0
            if ok:
                ok = _assert_no_leak("mixed folder", result.stdout, result.stderr)
            if ok:
                produced = [f for f in os.listdir(outdir)
                            if os.path.isfile(os.path.join(outdir, f))]
                if not produced:
                    print("       [mixed folder] PDF was not processed (no output)")
                    ok = False
            record("mixed/ folder → silent skip + PDF processed", ok)

    # 5. Non-existent file → non-zero exit, no leak.
    missing_path = os.path.join(REPO_ROOT, "samples/pdf/__missing_fixture__.pdf")
    result = run_cli([missing_path])
    ok = result.returncode != 0 and _assert_no_leak(
        "missing file", result.stdout, result.stderr
    )
    record("missing .pdf → non-zero exit + no leak", ok)

    # 6. No arguments → non-zero exit with help-style output on stderr.
    # Python argparse convention: missing required positional → exit 2, usage
    # on stderr. We assert non-zero (not a specific code) and that the help
    # text identifies itself as a usage message.
    result = run_cli([])
    stderr_text = (result.stderr or "").lower()
    stdout_text = (result.stdout or "").strip()
    ok = result.returncode != 0 and "usage" in stderr_text and not stdout_text
    record("no args → non-zero exit + usage on stderr", ok)

    # 7. Invalid option → non-zero exit (POSIX convention is 2; we only
    #    enforce non-zero to avoid coupling to a specific code).
    result = run_cli(["--no-such-option-xyz"])
    ok = result.returncode != 0
    record("invalid option → non-zero exit", ok)


# ---------------------------------------------------------------------------
# Folder summary line
#
# After traversing a folder argument, the CLI must print exactly one summary
# line per top-level argument — "No PDF files found in 'X'" or "Processed N
# PDF file(s) in 'X'". The summary is a *result*, not a log, so --quiet must
# NOT suppress it.
# ---------------------------------------------------------------------------

def verify_folder_summary() -> None:
    print("\n--- folder summary ---")

    # Empty folder → "No PDF files found"
    with tempfile.TemporaryDirectory() as folder, \
         tempfile.TemporaryDirectory() as outdir:
        result = run_cli([folder, "--output-dir", outdir])
        ok = result.returncode == 0 and "No PDF files found" in result.stdout
        if not ok:
            print(f"       [empty folder] stdout: {result.stdout[:200]!r}")
        record("empty folder → 'No PDF files found' + exit 0", ok)

    # Folder with 1 PDF → "Processed 1 PDF file"
    if not os.path.exists(PDF_BASIC):
        record("folder with 1 PDF [SKIPPED: PDF_BASIC missing]", None)
        record("folder with 3 PDFs [SKIPPED: PDF_BASIC missing]", None)
    else:
        with tempfile.TemporaryDirectory() as folder, \
             tempfile.TemporaryDirectory() as outdir:
            shutil.copy(PDF_BASIC, os.path.join(folder, "doc.pdf"))
            result = run_cli([folder, "--output-dir", outdir])
            ok = result.returncode == 0 and "Processed 1 PDF file" in result.stdout
            if not ok:
                print(f"       [1-pdf folder] stdout: {result.stdout[:200]!r}")
            record("folder with 1 PDF → 'Processed 1 PDF file'", ok)

        with tempfile.TemporaryDirectory() as folder, \
             tempfile.TemporaryDirectory() as outdir:
            for name in ("a.pdf", "b.pdf", "c.pdf"):
                shutil.copy(PDF_BASIC, os.path.join(folder, name))
            result = run_cli([folder, "--output-dir", outdir])
            ok = result.returncode == 0 and "Processed 3 PDF file" in result.stdout
            if not ok:
                print(f"       [3-pdf folder] stdout: {result.stdout[:200]!r}")
            record("folder with 3 PDFs → 'Processed 3 PDF file(s)'", ok)

    # --quiet must NOT suppress the summary line (result, not log).
    with tempfile.TemporaryDirectory() as folder, \
         tempfile.TemporaryDirectory() as outdir:
        result = run_cli([folder, "--quiet", "--output-dir", outdir])
        ok = result.returncode == 0 and "No PDF files found" in result.stdout
        if not ok:
            print(f"       [--quiet folder summary] stdout: {result.stdout[:200]!r}")
        record("--quiet preserves folder summary line", ok)


# ---------------------------------------------------------------------------
# --markdown-with-html
#
# --markdown-with-html is the first-class flag for HTML-in-Markdown output;
# --format markdown-with-html / --format markdown-with-images remain accepted
# values for one release but must emit a deprecation warning to stderr. After
# the next major release these tests can be inverted (deprecated values
# become hard errors).
# ---------------------------------------------------------------------------

def verify_markdown_with_html() -> None:
    print("\n--- --markdown-with-html ---")

    if not os.path.exists(PDF_TABLES):
        record("--markdown-with-html [SKIPPED: fixture missing]", None)
        record("--format markdown-with-html (deprecated) [SKIPPED]", None)
        record("--format markdown-with-images (deprecated) [SKIPPED]", None)
        return

    # First-class flag: --format markdown + --markdown-with-html.
    with tempfile.TemporaryDirectory() as outdir:
        result = run_cli([
            PDF_TABLES,
            "--format", "markdown",
            "--markdown-with-html",
            "--output-dir", outdir,
        ])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(outdir, ".md")
            ok = md_file is not None and assert_content(
                "--markdown-with-html", md_file,
                must_contain=["<table"],
                case_insensitive=True,
            )
        record("--markdown-with-html (first-class flag)", ok)

    # Deprecated value: --format markdown-with-html must still produce
    # HTML-in-markdown output AND emit a deprecation warning on stderr.
    with tempfile.TemporaryDirectory() as outdir:
        result = run_cli([
            PDF_TABLES,
            "--format", "markdown-with-html",
            "--output-dir", outdir,
        ])
        ok = result.returncode == 0
        # The wrapper relays JAR stderr to stdout in streaming mode, so the
        # warning may land on either stream — check both.
        combined = (result.stdout + result.stderr).lower()
        if ok and "deprecat" not in combined:
            print(f"       [deprecated markdown-with-html] no deprecation warning "
                  f"on stdout/stderr: {(result.stdout + result.stderr)[:200]!r}")
            ok = False
        if ok:
            md_file = _find_file_by_ext(outdir, ".md")
            ok = md_file is not None and assert_content(
                "deprecated markdown-with-html", md_file,
                must_contain=["<table"],
                case_insensitive=True,
            )
        record("--format markdown-with-html (deprecated, BC + warning)", ok)

    # Deprecated value: --format markdown-with-images must still succeed
    # and emit a deprecation warning. Output contents are not asserted —
    # the contract here is BC + warning, not equivalence.
    with tempfile.TemporaryDirectory() as outdir:
        result = run_cli([
            PDF_BASIC,
            "--format", "markdown-with-images",
            "--output-dir", outdir,
        ])
        ok = result.returncode == 0
        combined = (result.stdout + result.stderr).lower()
        if ok and "deprecat" not in combined:
            print(f"       [deprecated markdown-with-images] no deprecation "
                  f"warning: {(result.stdout + result.stderr)[:200]!r}")
            ok = False
        record("--format markdown-with-images (deprecated, BC + warning)", ok)


# ---------------------------------------------------------------------------
# Sanitization rules spot check
#
# Each default rule gets a representative trigger string. The contract per
# rule is:
#   - baseline (no --sanitize) extracts the trigger verbatim
#   - --sanitize variant strips the trigger and inserts the replacement
# If a rule regresses, exactly that label fails — making the regression
# easy to attribute. SCENARIOS list also acts as living documentation of
# what the default rule set covers.
#
# Triggers are chosen so each matches exactly one rule (no overlap), and
# replacements reflect the rule that actually fires. Rules whose regex is
# fully subsumed by an earlier rule (e.g. 15-digit caught by 10-18 digit,
# MAC caught by IPv6) are intentionally omitted — they are dead in the
# current rule order and listing them would falsely claim coverage.
# ---------------------------------------------------------------------------

SANITIZATION_SCENARIOS: list[tuple[str, str, str]] = [
    # (label, trigger in source PDF, expected replacement after --sanitize)
    ("email",               "alice.smith@example.org",         "email@example.com"),
    ("international phone", "+82-10-1234-5678",                "+00-0000-0000"),
    ("license/passport",    "AB1234567",                       "AA0000000"),
    ("credit card",         "1234-5678-9012-3456",             "0000-0000-0000-0000"),
    ("10-18 digit string",  "1234567890",                      "0000000000000000"),
    ("IPv4",                "192.168.0.42",                    "0.0.0.0"),
    ("IPv6",                "2001:db8:1234:5678:90ab:cdef:0:1", "0.0.0.0::1"),
    ("URL",                 "https://github.com/owner/repo",   "https://example.com"),
]


def verify_sanitization_rules() -> None:
    print("\n--- sanitization rule spot check ---")

    if not os.path.exists(PDF_SANITIZATION_TARGETS):
        for label, _trigger, _replacement in SANITIZATION_SCENARIOS:
            record(f"--sanitize {label} [SKIPPED: fixture missing]", None)
        return

    with tempfile.TemporaryDirectory() as base_dir, \
         tempfile.TemporaryDirectory() as san_dir:
        base = run_cli([PDF_SANITIZATION_TARGETS,
                        "--format", "text", "--output-dir", base_dir])
        san = run_cli([PDF_SANITIZATION_TARGETS,
                       "--format", "text", "--sanitize", "--output-dir", san_dir])

        if base.returncode != 0 or san.returncode != 0:
            for label, _trigger, _replacement in SANITIZATION_SCENARIOS:
                record(f"--sanitize {label} [CLI run failed]", False)
            return

        base_file = _find_file_by_ext(base_dir, ".txt")
        san_file  = _find_file_by_ext(san_dir, ".txt")
        if not (base_file and san_file):
            for label, _trigger, _replacement in SANITIZATION_SCENARIOS:
                record(f"--sanitize {label} [missing output file]", False)
            return

        base_text = open(base_file, encoding="utf-8", errors="replace").read()
        san_text  = open(san_file,  encoding="utf-8", errors="replace").read()

        for label, trigger, replacement in SANITIZATION_SCENARIOS:
            if trigger not in base_text:
                # Fixture self-check: if baseline lacks the trigger, the
                # fixture is broken — not the rule. Report explicitly so
                # we don't blame the wrong layer.
                print(f"       [{label}] trigger not in baseline text — "
                      f"fixture problem, not a sanitization regression. "
                      f"trigger={trigger!r}")
                record(f"--sanitize {label} [fixture: trigger missing in baseline]", False)
                continue
            if trigger in san_text:
                print(f"       [{label}] trigger survived --sanitize: {trigger!r}")
                record(f"--sanitize {label}", False)
                continue
            if replacement not in san_text:
                print(f"       [{label}] expected replacement not found: "
                      f"{replacement!r}")
                record(f"--sanitize {label}", False)
                continue
            record(f"--sanitize {label}", True)


# ---------------------------------------------------------------------------
# Main test suite
# ---------------------------------------------------------------------------

def main() -> None:
    check_option_coverage()

    print("=" * 60)
    print("opendataloader-pdf CLI Verification")
    print("=" * 60)

    # ------------------------------------------------------------------
    # --format markdown
    # ------------------------------------------------------------------
    print("\n--- --format ---")

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "markdown", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None and assert_content(
                "--format markdown", md_file, must_contain=None
            )
            if ok:
                # Check for markdown markers: # or *
                with open(md_file, "r", encoding="utf-8", errors="replace") as fh:
                    content = fh.read()
                ok = "#" in content or "*" in content
                if not ok:
                    print("       [content] missing '#' or '*' in markdown output")
        record("--format markdown", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "html", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            html_file = _find_file_by_ext(tmpdir, ".html")
            ok = html_file is not None and assert_content(
                "--format html", html_file,
                must_contain=["<html"],
                case_insensitive=True,
            )
            if not ok and html_file is not None:
                ok = assert_content(
                    "--format html <p", html_file,
                    must_contain=["<p"],
                    case_insensitive=True,
                )
        record("--format html", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "text", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            txt_file = _find_file_by_ext(tmpdir, ".txt")
            ok = txt_file is not None
            if ok:
                with open(txt_file, "r", encoding="utf-8", errors="replace") as fh:
                    ok = bool(fh.read().strip())
                if not ok:
                    print("       [content] text output is empty")
        record("--format text", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "json", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            json_file = _find_file_by_ext(tmpdir, ".json")
            ok = json_file is not None
            if ok:
                try:
                    with open(json_file, "r", encoding="utf-8") as fh:
                        json.load(fh)
                except json.JSONDecodeError as exc:
                    print(f"       [content] invalid JSON: {exc}")
                    ok = False
        record("--format json", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "pdf", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            pdf_out = _find_file_by_ext(tmpdir, ".pdf")
            ok = pdf_out is not None
            if ok:
                with open(pdf_out, "rb") as fh:
                    header = fh.read(5)
                ok = header == b"%PDF-"
                if not ok:
                    print(f"       [content] bad PDF header: {header!r}")
        record("--format pdf", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "tagged-pdf", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            pdf_out = _find_file_by_ext(tmpdir, ".pdf")
            ok = pdf_out is not None
            if ok:
                with open(pdf_out, "rb") as fh:
                    header = fh.read(5)
                ok = header == b"%PDF-"
                if not ok:
                    print(f"       [content] bad tagged-PDF header: {header!r}")
        record("--format tagged-pdf", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_TABLES, "--format", "markdown-with-html", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None and assert_content(
                "--format markdown-with-html", md_file,
                must_contain=["<table"],
                case_insensitive=True,
            )
        record("--format markdown-with-html", ok)

    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_IMAGES, "--format", "markdown-with-images", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None
            if ok:
                with open(md_file, "r", encoding="utf-8", errors="replace") as fh:
                    content = fh.read()
                ok = "![" in content or "data:image/" in content
                if not ok:
                    print("       [content] missing '![' or 'data:image/' in output")
        record("--format markdown-with-images", ok)

    # Multi-format
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([PDF_BASIC, "--format", "markdown,json,html", "--output-dir", tmpdir])
        ok = result.returncode == 0
        if ok:
            md_file   = _find_file_by_ext(tmpdir, ".md")
            json_file = _find_file_by_ext(tmpdir, ".json")
            html_file = _find_file_by_ext(tmpdir, ".html")
            ok = all([md_file, json_file, html_file])
            if ok:
                for path in [md_file, json_file, html_file]:
                    with open(path, "r", encoding="utf-8", errors="replace") as fh:
                        if not fh.read().strip():
                            print(f"       [content] empty output file: {path}")
                            ok = False
                            break
        record("--format markdown,json,html (multi)", ok)

    # ------------------------------------------------------------------
    # --output-dir
    # ------------------------------------------------------------------
    print("\n--- --output-dir ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        result = smoke("--output-dir", [PDF_BASIC, "--output-dir", tmpdir], tmpdir)
        record("--output-dir", result)

    # ------------------------------------------------------------------
    # --quiet
    # ------------------------------------------------------------------
    print("\n--- --quiet ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        result = assert_stderr_empty(
            "--quiet",
            [PDF_BASIC, "--quiet", "--output-dir", tmpdir],
        )
        record("--quiet", result)

    # ------------------------------------------------------------------
    # --to-stdout
    # ------------------------------------------------------------------
    print("\n--- --to-stdout ---")
    result = assert_stdout_nonempty(
        "--to-stdout",
        [PDF_BASIC, "--to-stdout", "--format", "text"],
    )
    record("--to-stdout", result)

    # ------------------------------------------------------------------
    # --password
    # ------------------------------------------------------------------
    print("\n--- --password ---")
    if not os.path.exists(PDF_PASSWORD):
        record("--password [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = smoke(
                "--password",
                [PDF_PASSWORD, "--password", PASSWORD, "--output-dir", tmpdir],
                tmpdir,
            )
            record("--password", result)

    # ------------------------------------------------------------------
    # --keep-line-breaks
    # ------------------------------------------------------------------
    print("\n--- --keep-line-breaks ---")
    if not os.path.exists(PDF_MULTIPAGE):
        record("--keep-line-breaks [SKIPPED: fixture missing]", None)
    else:
        result = run_comparison(
            "--keep-line-breaks",
            cmd_baseline=[PDF_MULTIPAGE, "--format", "text"],
            cmd_variant=[PDF_MULTIPAGE, "--format", "text", "--keep-line-breaks"],
            outdir="",
            expect="differ",
        )
        record("--keep-line-breaks", result)

    # ------------------------------------------------------------------
    # --replace-invalid-chars
    # ------------------------------------------------------------------
    print("\n--- --replace-invalid-chars ---")
    if not os.path.exists(PDF_INVALID_CHARS):
        record("--replace-invalid-chars [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_INVALID_CHARS,
                "--replace-invalid-chars", "_",
                "--format", "text",
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                txt_file = _find_file_by_ext(tmpdir, ".txt")
                ok = txt_file is not None and assert_content(
                    "--replace-invalid-chars", txt_file, must_contain=["_"]
                )
            record("--replace-invalid-chars \"_\"", ok)

    # ------------------------------------------------------------------
    # --pages
    # ------------------------------------------------------------------
    print("\n--- --pages ---")
    if not os.path.exists(PDF_MULTIPAGE):
        record("--pages 1 [SKIPPED: fixture missing]", None)
        record("--pages 1-2 [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as full_dir, \
             tempfile.TemporaryDirectory() as p1_dir:
            run_cli([PDF_MULTIPAGE, "--format", "text", "--output-dir", full_dir])
            run_cli([PDF_MULTIPAGE, "--format", "text", "--pages", "1", "--output-dir", p1_dir])
            full_file = _find_file_by_ext(full_dir, ".txt")
            p1_file   = _find_file_by_ext(p1_dir, ".txt")
            if full_file and p1_file:
                full_size = os.path.getsize(full_file)
                ok = assert_file_size("--pages 1", p1_file, smaller_than=full_size)
            else:
                print("       [pages 1] missing output file(s)")
                ok = False
            record("--pages 1", ok)

        with tempfile.TemporaryDirectory() as full_dir, \
             tempfile.TemporaryDirectory() as p12_dir:
            run_cli([PDF_MULTIPAGE, "--format", "text", "--output-dir", full_dir])
            run_cli([PDF_MULTIPAGE, "--format", "text", "--pages", "1-2", "--output-dir", p12_dir])
            full_file = _find_file_by_ext(full_dir, ".txt")
            p12_file  = _find_file_by_ext(p12_dir, ".txt")
            if full_file and p12_file:
                full_size = os.path.getsize(full_file)
                ok = assert_file_size("--pages 1-2", p12_file, smaller_than=full_size)
            else:
                print("       [pages 1-2] missing output file(s)")
                ok = False
            record("--pages 1-2", ok)

    # ------------------------------------------------------------------
    # --include-header-footer
    # ------------------------------------------------------------------
    print("\n--- --include-header-footer ---")
    if not os.path.exists(PDF_STRUCTURED):
        record("--include-header-footer [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as base_dir, \
             tempfile.TemporaryDirectory() as hf_dir:
            run_cli([PDF_STRUCTURED, "--format", "text", "--output-dir", base_dir])
            run_cli([PDF_STRUCTURED, "--format", "text", "--include-header-footer",
                     "--output-dir", hf_dir])
            base_file = _find_file_by_ext(base_dir, ".txt")
            hf_file   = _find_file_by_ext(hf_dir, ".txt")
            if base_file and hf_file:
                base_size = os.path.getsize(base_file)
                ok = assert_file_size("--include-header-footer", hf_file, larger_than=base_size)
            else:
                print("       [include-header-footer] missing output file(s)")
                ok = False
            record("--include-header-footer", ok)

    # ------------------------------------------------------------------
    # --detect-strikethrough
    # ------------------------------------------------------------------
    print("\n--- --detect-strikethrough ---")
    if STRIKETHROUGH_KNOWN_ISSUE:
        record("--detect-strikethrough [SKIPPED: known issue, see tasks#348]", None)
    elif not os.path.exists(PDF_STRIKETHROUGH):
        record("--detect-strikethrough [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_STRIKETHROUGH,
                "--detect-strikethrough",
                "--format", "markdown",
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                md_file = _find_file_by_ext(tmpdir, ".md")
                ok = md_file is not None and assert_content(
                    "--detect-strikethrough", md_file, must_contain=["~~"]
                )
            record("--detect-strikethrough", ok)

    # ------------------------------------------------------------------
    # --sanitize
    # ------------------------------------------------------------------
    print("\n--- --sanitize ---")
    if not os.path.exists(PDF_SANITIZE):
        record("--sanitize [SKIPPED: fixture missing]", None)
    else:
        result = run_comparison(
            "--sanitize",
            cmd_baseline=[PDF_SANITIZE, "--format", "text"],
            cmd_variant=[PDF_SANITIZE, "--format", "text", "--sanitize"],
            outdir="",
            expect="differ",
        )
        record("--sanitize", result)

    # ------------------------------------------------------------------
    # --content-safety-off
    # ------------------------------------------------------------------
    print("\n--- --content-safety-off ---")
    if not os.path.exists(PDF_SAFETY):
        record("--content-safety-off all [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = smoke(
                "--content-safety-off all",
                [PDF_SAFETY, "--content-safety-off", "all", "--format", "text",
                 "--output-dir", tmpdir],
                tmpdir,
            )
            record("--content-safety-off all", result)

    # ------------------------------------------------------------------
    # --use-struct-tree
    # ------------------------------------------------------------------
    print("\n--- --use-struct-tree ---")
    if not os.path.exists(PDF_STRUCTURED):
        record("--use-struct-tree [SKIPPED: fixture missing]", None)
    else:
        result = run_comparison(
            "--use-struct-tree",
            cmd_baseline=[PDF_STRUCTURED, "--format", "text"],
            cmd_variant=[PDF_STRUCTURED, "--format", "text", "--use-struct-tree"],
            outdir="",
            expect="differ",
        )
        record("--use-struct-tree", result)

    # ------------------------------------------------------------------
    # --table-method
    # ------------------------------------------------------------------
    print("\n--- --table-method ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_TABLES,
            "--table-method", "cluster",
            "--format", "markdown",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None and assert_content(
                "--table-method cluster", md_file, must_contain=["|"]
            )
        record("--table-method cluster", ok)

    # ------------------------------------------------------------------
    # --reading-order
    # ------------------------------------------------------------------
    print("\n--- --reading-order ---")
    if not os.path.exists(PDF_MULTIPAGE):
        record("--reading-order off [SKIPPED: fixture missing]", None)
    else:
        result = run_comparison(
            "--reading-order off",
            cmd_baseline=[PDF_MULTIPAGE, "--format", "text"],
            cmd_variant=[PDF_MULTIPAGE, "--format", "text", "--reading-order", "off"],
            outdir="",
            expect="differ",
        )
        record("--reading-order off", result)

    # ------------------------------------------------------------------
    # --threads
    # ------------------------------------------------------------------
    print("\n--- --threads ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        result = smoke(
            "--threads 4",
            [PDF_BASIC, "--threads", "4", "--output-dir", tmpdir],
            tmpdir,
        )
        record("--threads 4", result)

    # ------------------------------------------------------------------
    # --image-output
    # ------------------------------------------------------------------
    print("\n--- --image-output ---")

    # --image-output off: no image files generated
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "off",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            img_files = _find_image_files(tmpdir)
            ok = len(img_files) == 0
            if not ok:
                print(f"       [image-output off] found {len(img_files)} image file(s), expected 0")
        record("--image-output off", ok)

    # --image-output external: image files created separately
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "external",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            img_files = _find_image_files(tmpdir)
            ok = len(img_files) > 0
            if not ok:
                print("       [image-output external] no image files created")
        record("--image-output external", ok)

    # --image-output embedded: markdown contains data:image/
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "embedded",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None and assert_content(
                "--image-output embedded", md_file, must_contain=["data:image/"]
            )
        record("--image-output embedded", ok)

    # ------------------------------------------------------------------
    # --image-format
    # ------------------------------------------------------------------
    print("\n--- --image-format ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "external",
            "--image-format", "jpeg",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            # Look for .jpg or .jpeg files and check JPEG header
            jpeg_files = []
            for root, _dirs, files in os.walk(tmpdir):
                for name in files:
                    if name.lower().endswith((".jpg", ".jpeg")):
                        jpeg_files.append(os.path.join(root, name))
            ok = len(jpeg_files) > 0
            if ok:
                with open(jpeg_files[0], "rb") as fh:
                    header = fh.read(2)
                ok = header == b"\xff\xd8"
                if not ok:
                    print(f"       [image-format jpeg] bad JPEG header: {header!r}")
            else:
                print("       [image-format jpeg] no .jpg/.jpeg files found")
        record("--image-format jpeg", ok)

    # ------------------------------------------------------------------
    # --image-dir
    # ------------------------------------------------------------------
    print("\n--- --image-dir ---")
    with tempfile.TemporaryDirectory() as tmpdir:
        img_dir = os.path.join(tmpdir, "my_images")
        os.makedirs(img_dir)
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "external",
            "--image-dir", img_dir,
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            img_files = _find_image_files(img_dir)
            ok = len(img_files) > 0
            if not ok:
                print(f"       [image-dir] no image files in {img_dir}")
        record("--image-dir", ok)

    # ------------------------------------------------------------------
    # Page separators
    # ------------------------------------------------------------------
    print("\n--- page separators ---")

    if not os.path.exists(PDF_MULTIPAGE):
        record("--markdown-page-separator [SKIPPED: fixture missing]", None)
        record("--text-page-separator [SKIPPED: fixture missing]", None)
        record("--html-page-separator [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_MULTIPAGE,
                "--format", "markdown",
                # Distinctive sentinel: a bare "---" also occurs as a markdown
                # thematic break / table rule / front-matter fence, so asserting
                # on it would pass even if the separator option were ignored.
                "--markdown-page-separator=ODLPAGEBREAK",
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                md_file = _find_file_by_ext(tmpdir, ".md")
                ok = md_file is not None and assert_content(
                    "--markdown-page-separator", md_file, must_contain=["ODLPAGEBREAK"]
                )
            record("--markdown-page-separator ODLPAGEBREAK", ok)

        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_MULTIPAGE,
                "--format", "text",
                "--text-page-separator", "PAGEBREAK",
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                txt_file = _find_file_by_ext(tmpdir, ".txt")
                ok = txt_file is not None and assert_content(
                    "--text-page-separator PAGEBREAK", txt_file, must_contain=["PAGEBREAK"]
                )
            record("--text-page-separator PAGEBREAK", ok)

        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_MULTIPAGE,
                "--format", "html",
                # Distinctive marker class: a bare "<hr" can occur in normal HTML
                # output, so a custom attribute proves the separator value is honored.
                "--html-page-separator", '<hr class="odl-pagebreak"/>',
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                html_file = _find_file_by_ext(tmpdir, ".html")
                ok = html_file is not None and assert_content(
                    "--html-page-separator", html_file, must_contain=["odl-pagebreak"]
                )
            record("--html-page-separator odl-pagebreak", ok)

    # ------------------------------------------------------------------
    # Combo tests
    # ------------------------------------------------------------------
    print("\n--- Combo tests ---")

    # --image-output embedded + --image-format jpeg
    with tempfile.TemporaryDirectory() as tmpdir:
        result = run_cli([
            PDF_IMAGES,
            "--format", "markdown",
            "--image-output", "embedded",
            "--image-format", "jpeg",
            "--output-dir", tmpdir,
        ])
        ok = result.returncode == 0
        if ok:
            md_file = _find_file_by_ext(tmpdir, ".md")
            ok = md_file is not None and assert_content(
                "--image-output embedded + --image-format jpeg",
                md_file,
                must_contain=["data:image/jpeg"],
            )
        record("--image-output embedded + --image-format jpeg", ok)

    # --keep-line-breaks + --detect-strikethrough + --sanitize
    if not os.path.exists(PDF_SANITIZE):
        record("--keep-line-breaks + --detect-strikethrough + --sanitize [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_cli([
                PDF_SANITIZE,
                "--keep-line-breaks",
                "--detect-strikethrough",
                "--sanitize",
                "--format", "markdown",
                "--output-dir", tmpdir,
            ])
            ok = result.returncode == 0
            if ok:
                md_file = _find_file_by_ext(tmpdir, ".md")
                ok = md_file is not None
                if ok:
                    ok = os.path.getsize(md_file) > 0
                    if not ok:
                        print("       [combo] output file is empty")
            record("--keep-line-breaks + --detect-strikethrough + --sanitize", ok)

    # --pages 1,2 + --format markdown,json
    if not os.path.exists(PDF_MULTIPAGE):
        record("--pages 1,2 + --format markdown,json [SKIPPED: fixture missing]", None)
    else:
        with tempfile.TemporaryDirectory() as full_dir, \
             tempfile.TemporaryDirectory() as p12_dir:
            run_cli([PDF_MULTIPAGE, "--format", "markdown,json", "--output-dir", full_dir])
            run_cli([
                PDF_MULTIPAGE,
                "--pages", "1,2",
                "--format", "markdown,json",
                "--output-dir", p12_dir,
            ])
            full_md   = _find_file_by_ext(full_dir, ".md")
            full_json = _find_file_by_ext(full_dir, ".json")
            p12_md    = _find_file_by_ext(p12_dir, ".md")
            p12_json  = _find_file_by_ext(p12_dir, ".json")
            ok = all([full_md, full_json, p12_md, p12_json])
            if ok:
                full_md_size   = os.path.getsize(full_md)
                full_json_size = os.path.getsize(full_json)
                ok = (
                    assert_file_size("--pages 1,2 md",   p12_md,   smaller_than=full_md_size)
                    and
                    assert_file_size("--pages 1,2 json",  p12_json, smaller_than=full_json_size)
                )
            else:
                print("       [pages 1,2 + multi-format] missing output file(s)")
            record("--pages 1,2 + --format markdown,json", ok)

    # --quiet + --to-stdout: stdout still contains output
    result = assert_stdout_nonempty(
        "--quiet + --to-stdout",
        [PDF_BASIC, "--quiet", "--to-stdout", "--format", "text"],
    )
    record("--quiet + --to-stdout", result)

    # ------------------------------------------------------------------
    # Encoding safety: --help under cp949 codepage
    # ------------------------------------------------------------------
    print("\n--- --help cp949 encoding safety ---")
    record("--help cp949 safe (main)", _verify_help_cp949_safe(
        [sys.executable, "-m", "opendataloader_pdf"]
    ))
    record("--help cp949 safe (hybrid)", _verify_hybrid_help_cp949_safe())

    # ------------------------------------------------------------------
    # Hybrid backend failure contracts
    # ------------------------------------------------------------------
    print("\n--- hybrid backend failure contracts ---")
    record(
        "hybrid fail-fast on unreachable backend",
        _verify_hybrid_fail_fast_on_unreachable_backend(),
    )

    # ------------------------------------------------------------------
    # Input/output contract verifications (independent of CLI options)
    # ------------------------------------------------------------------
    verify_input_validation()
    verify_folder_summary()
    verify_markdown_with_html()
    verify_sanitization_rules()

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    passed  = sum(1 for _, v in RESULTS if v is True)
    failed  = sum(1 for _, v in RESULTS if v is False)
    skipped = sum(1 for _, v in RESULTS if v is None)

    print()
    print("=" * 60)
    print(f"Results: {passed} passed, {failed} failed, {skipped} skipped")
    print("=" * 60)

    # Determine report filename
    is_ci = os.environ.get("CI", "").lower() in ("true", "1", "yes")
    report_name = "verification-report-ci.txt" if is_ci else "verification-report-local.txt"
    report_path = os.path.join(REPO_ROOT, "verification", report_name)

    with open(report_path, "w", encoding="utf-8") as rpt:
        rpt.write("opendataloader-pdf CLI Verification Report\n")
        rpt.write("=" * 60 + "\n")
        for label, status in RESULTS:
            if status is None:
                marker = "SKIP"
            elif status:
                marker = "PASS"
            else:
                marker = "FAIL"
            rpt.write(f"  {marker} | {label}\n")
        rpt.write("\n")
        rpt.write(f"Results: {passed} passed, {failed} failed, {skipped} skipped\n")

    print(f"Report written to: {report_path}")

    # Append to GitHub Step Summary if available
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as gs:
            gs.write("## opendataloader-pdf CLI Verification\n\n")
            gs.write("| Status | Test |\n")
            gs.write("|--------|------|\n")
            for label, status in RESULTS:
                if status is None:
                    marker = "SKIP"
                elif status:
                    marker = "PASS"
                else:
                    marker = "FAIL"
                gs.write(f"| {marker} | {label} |\n")
            gs.write(f"\n**Results: {passed} passed, {failed} failed, {skipped} skipped**\n")

    sys.exit(1 if failed > 0 else 0)


if __name__ == "__main__":
    main()
