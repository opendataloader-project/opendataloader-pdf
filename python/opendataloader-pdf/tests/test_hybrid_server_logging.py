"""Tests for the diagnostic logging added for issue #414.

The report there ("why it runs very slowly in CPU mode") could not be answered
from the server log: the 130s model loads left no trace, an unparseable page
range was dropped silently, and `--log-level` never reached our own or
docling's loggers. These tests pin the behaviour that makes such a report
answerable without asking the reporter to reproduce it.
"""

import logging

import pytest

from opendataloader_pdf import hybrid_server


# Every logger `configure_logging` touches, so the fixture can put each one
# back rather than leaving a level set for whatever runs next.
_TOUCHED_LOGGERS = (
    "docling",
    "httpx",
    "httpcore",
    "urllib3",
    "filelock",
    "uvicorn",
    "uvicorn.error",
    "uvicorn.access",
)


@pytest.fixture(autouse=True)
def restore_logging():
    """Leave global logging state exactly as the test session found it.

    Restoring only the root logger would leak the per-logger levels and the
    `propagate` flags these tests set, changing how later tests -- and pytest's
    own capture -- behave.
    """
    root = logging.getLogger()
    saved_handlers = list(root.handlers)
    saved_level = root.level
    saved = {
        name: (
            logging.getLogger(name).level,
            logging.getLogger(name).propagate,
            list(logging.getLogger(name).handlers),
        )
        for name in _TOUCHED_LOGGERS
    }

    yield

    for handler in list(root.handlers):
        if handler not in saved_handlers:
            root.removeHandler(handler)
            if getattr(handler, "name", None) == hybrid_server._CONSOLE_HANDLER:
                handler.close()
    for handler in saved_handlers:
        if handler not in root.handlers:
            root.addHandler(handler)
    root.setLevel(saved_level)

    for name, (level, propagate, handlers) in saved.items():
        logger_obj = logging.getLogger(name)
        logger_obj.setLevel(level)
        logger_obj.propagate = propagate
        logger_obj.handlers = handlers


def test_log_level_reaches_our_logger_and_docling():
    """`--log-level` used to reach only uvicorn, leaving the rest pinned at INFO."""
    hybrid_server.configure_logging("debug")
    assert logging.getLogger().level == logging.DEBUG
    assert logging.getLogger("docling").level == logging.DEBUG

    hybrid_server.configure_logging("warning")
    assert logging.getLogger().level == logging.WARNING
    assert logging.getLogger("docling").level == logging.WARNING


def test_http_client_loggers_stay_quiet():
    """HuggingFace metadata requests are noise at INFO; docling's own lines are not."""
    hybrid_server.configure_logging("info")
    assert logging.getLogger("httpx").level == logging.WARNING
    assert logging.getLogger("docling").level == logging.INFO


def test_configure_logging_preserves_foreign_handlers():
    """dictConfig would otherwise detach handlers owned by an embedding app or pytest."""
    root = logging.getLogger()
    foreign = logging.NullHandler()
    root.addHandler(foreign)

    hybrid_server.configure_logging("info")

    assert foreign in logging.getLogger().handlers


def test_configure_logging_is_idempotent():
    """Repeat calls replace our own console handler instead of stacking copies."""
    hybrid_server.configure_logging("info")
    before = len(logging.getLogger().handlers)
    hybrid_server.configure_logging("info")
    assert len(logging.getLogger().handlers) == before


def test_request_id_is_rendered_on_every_record(caplog):
    """Lines from one request share an id; startup lines fall back to '-'."""
    hybrid_server.configure_logging("info")
    filt = hybrid_server._RequestIdFilter()

    record = logging.LogRecord("x", logging.INFO, "f", 1, "msg", None, None)
    filt.filter(record)
    assert record.request_id == "-"

    token = hybrid_server._request_id.set("abc123")
    try:
        record = logging.LogRecord("x", logging.INFO, "f", 1, "msg", None, None)
        filt.filter(record)
        assert record.request_id == "abc123"
    finally:
        hybrid_server._request_id.reset(token)


def test_debug_format_adds_thread_and_logger_columns():
    """Thread and logger name matter when chasing concurrency, and only then."""
    assert "threadName" in hybrid_server._LOG_FORMAT_DEBUG
    assert "threadName" not in hybrid_server._LOG_FORMAT
    assert "request_id" in hybrid_server._LOG_FORMAT


def test_probe_page_count_reads_a_real_pdf(tmp_path):
    """The page count annotates request lines, so a slow request states its size."""
    import pypdfium2

    pdf = pypdfium2.PdfDocument.new()
    for _ in range(3):
        pdf.new_page(200, 200)
    path = tmp_path / "three.pdf"
    pdf.save(str(path))

    assert hybrid_server._probe_page_count(str(path)) == 3


def test_probe_page_count_returns_none_for_unreadable_input(tmp_path):
    """A damaged upload may still convert; a missing count must not fail the request."""
    broken = tmp_path / "broken.pdf"
    broken.write_bytes(b"not a pdf")
    assert hybrid_server._probe_page_count(str(broken)) is None


def test_heartbeat_reports_while_a_conversion_runs(caplog):
    """Distinguishes a working conversion from a hung one -- the #414 ambiguity."""
    hybrid_server.configure_logging("info")
    original = hybrid_server._PROGRESS_INTERVAL_S
    hybrid_server._PROGRESS_INTERVAL_S = 0.05
    try:
        with caplog.at_level(logging.INFO):
            with hybrid_server._ConversionHeartbeat("report.pdf", 14):
                import time

                time.sleep(0.16)
    finally:
        hybrid_server._PROGRESS_INTERVAL_S = original

    lines = [r for r in caplog.messages if "still converting" in r]
    assert lines, "expected at least one heartbeat line"
    assert "report.pdf" in caplog.text
    assert "14 pages" in caplog.text


def test_heartbeat_is_silent_for_a_quick_conversion(caplog):
    """No line at all when the work finishes inside one interval."""
    hybrid_server.configure_logging("info")
    original = hybrid_server._PROGRESS_INTERVAL_S
    hybrid_server._PROGRESS_INTERVAL_S = 30.0
    try:
        with caplog.at_level(logging.INFO):
            with hybrid_server._ConversionHeartbeat("quick.pdf", 1):
                pass
    finally:
        hybrid_server._PROGRESS_INTERVAL_S = original

    assert "still converting" not in caplog.text


def test_filename_cannot_forge_a_log_line():
    """An uploaded name is caller-controlled; a newline in it would write its own line."""
    forged = "ok.pdf\n2026-01-01 00:00:00.000 | ERROR   | 000000 | forged"
    safe = hybrid_server._safe_log_name(forged)
    assert "\n" not in safe
    assert "forged" in safe, "content is kept, just made harmless"


def test_filename_cannot_carry_terminal_escapes():
    """ANSI escapes execute when an operator views the log with cat or tail."""
    safe = hybrid_server._safe_log_name("a\x1b[2J\x1b[31mEVIL.pdf")
    assert "\x1b" not in safe


def test_filename_is_bounded_and_keeps_real_unicode():
    """Truncated so one upload cannot dominate the log; legitimate names survive."""
    assert len(hybrid_server._safe_log_name("x" * 5000)) < 200
    assert hybrid_server._safe_log_name("üñîçode-한글.pdf") == "üñîçode-한글.pdf"
    assert hybrid_server._safe_log_name(None) == "unnamed"


def test_importing_the_module_leaves_host_logging_alone():
    """`basicConfig` was a no-op once handlers existed; dictConfig is not, so
    configuration belongs in main() rather than at import."""
    root = logging.getLogger()
    host = logging.NullHandler()
    root.addHandler(host)
    root.setLevel(logging.ERROR)

    import importlib

    importlib.reload(hybrid_server)

    assert root.level == logging.ERROR, "import must not reset the host's level"
    assert host in root.handlers

    hybrid_server.configure_logging("info")


def test_unreadable_timings_warn_once_not_per_step():
    """A docling API change fails every step; one line per step would bury the log."""

    class Broken:
        count = 1

        def total(self):
            raise RuntimeError("api changed")

        def avg(self):
            return 0.0

    class Result:
        timings = {f"step{i}": Broken() for i in range(8)}

    logger = logging.getLogger("opendataloader_pdf.hybrid_server")
    records = []

    class Capture(logging.Handler):
        def emit(self, record):
            records.append(record)

    handler = Capture()
    logger.addHandler(handler)
    try:
        assert hybrid_server.extract_timings(Result()) == {}
    finally:
        logger.removeHandler(handler)

    warnings = [r for r in records if r.levelno == logging.WARNING]
    assert len(warnings) == 1, f"expected one summary, got {len(warnings)}"


def test_heartbeat_spacing_widens_as_a_conversion_runs_on():
    """A flat interval spent 31 lines on an eight-minute conversion."""
    interval_at = hybrid_server._ConversionHeartbeat._interval_at

    assert interval_at(0) == 15.0
    assert interval_at(59) == 15.0
    assert interval_at(60) == 60.0
    assert interval_at(299) == 60.0
    assert interval_at(300) == 120.0
    assert interval_at(10_000) == 120.0

    elapsed, lines = 0.0, 0
    while elapsed < 480:
        elapsed += interval_at(elapsed)
        lines += 1
    assert lines <= 12, f"eight minutes should stay around ten lines, got {lines}"


def test_client_address_falls_back_when_absent():
    """A Unix-socket connection has no peer address; the line still has to render."""

    class NoClient:
        client = None

    class Peer:
        class client:
            host = "192.168.1.50"

    assert hybrid_server._client_address(NoClient()) == "unknown"
    assert hybrid_server._client_address(object()) == "unknown"
    assert hybrid_server._client_address(Peer()) == "192.168.1.50"


def test_configure_logging_does_not_close_a_foreign_handler():
    """A handler that latches on close() would survive as an object but stop
    recording, which is how a non-incremental dictConfig broke host logging."""

    class CloseSensitive(logging.Handler):
        def __init__(self):
            super().__init__()
            self.alive = True
            self.seen = []

        def close(self):
            self.alive = False
            super().close()

        def emit(self, record):
            if self.alive:
                self.seen.append(record.getMessage())

    host = CloseSensitive()
    root = logging.getLogger()
    root.addHandler(host)
    root.setLevel(logging.INFO)

    hybrid_server.configure_logging("info")

    assert host in logging.getLogger().handlers
    assert host.alive, "configure_logging must not close a handler it does not own"

    logging.getLogger("some.host.logger").info("host line")
    assert host.seen == ["host line"], "the host handler must still record"


def test_repeat_calls_replace_only_our_own_handler():
    """The console handler is ours to swap; anything else stays untouched."""
    root = logging.getLogger()
    host = logging.NullHandler()
    root.addHandler(host)

    hybrid_server.configure_logging("info")
    hybrid_server.configure_logging("debug")
    hybrid_server.configure_logging("info")

    ours = [
        h
        for h in root.handlers
        if getattr(h, "name", None) == hybrid_server._CONSOLE_HANDLER
    ]
    assert len(ours) == 1, f"expected exactly one console handler, got {len(ours)}"
    assert host in root.handlers


def test_request_ids_are_wide_enough_to_stay_distinct():
    """Three bytes collided at ~3% over a thousand requests, often enough to
    mislead someone correlating lines.

    Asserts the width rather than drawing ids and checking them: a random draw
    collides with non-zero probability even when the width is right, so that
    test would fail occasionally on correct code.
    """
    assert hybrid_server._REQUEST_ID_BYTES >= 6


def test_completion_counts_converted_pages_not_document_length():
    """A 3-page range out of 14 reported "14 pages" and 0.34s/pg for work that
    cost 1.60s/pg -- the per-page figure is the one someone reads to judge speed."""
    converted = hybrid_server._converted_page_count

    assert converted(None, 14) == 14, "whole document: the length is the answer"
    assert converted((1, 3), 14) == 3
    assert converted((5, 5), 14) == 1
    assert converted((1, 99), 14) == 14, "a range past the end stops at the end"
    assert converted((1, 3), None) == 3, "usable even when the length is unknown"
    assert converted(None, None) is None
    assert converted((10, 2), 14) == 0, "an inverted range converts nothing"


def test_page_ranges_is_bounded_before_logging():
    """Starlette allows a 1MiB form field, so an unbounded echo puts a megabyte
    on one line -- and repeated, floods the log this module exists to keep readable."""
    bounded = hybrid_server._bounded

    assert bounded("1-5-9") == "1-5-9", "a normal value is untouched"

    logged = bounded("9" * 500_000)
    assert len(logged) < 200
    assert "500000 chars" in logged, "the real size is still reported"


def test_child_loggers_keep_foreign_handlers():
    """An embedding application may route docling's output to its own sink;
    stripping handlers from the loggers we configure would silence it."""

    received = []

    class HostSink(logging.Handler):
        def emit(self, record):
            received.append(record.getMessage())

    host = HostSink()
    logging.getLogger("docling").addHandler(host)

    hybrid_server.configure_logging("info")

    assert host in logging.getLogger("docling").handlers
    logging.getLogger("docling.pipeline").info("a docling line")
    assert received == ["a docling line"], "the host's sink must still receive"


def test_uvicorn_logger_propagation_is_restored():
    """uvicorn sets propagate=False on its loggers; left alone, its startup
    lines would never reach our console in our format."""
    logging.getLogger("uvicorn").propagate = False
    logging.getLogger("uvicorn.error").propagate = False

    hybrid_server.configure_logging("info")

    assert logging.getLogger("uvicorn").propagate is True
    assert logging.getLogger("uvicorn.error").propagate is True
