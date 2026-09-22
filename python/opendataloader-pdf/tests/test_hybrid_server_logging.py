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

    lines = [r for r in caplog.messages if "converting file=" in r]
    assert lines, "expected at least one heartbeat line"
    assert "file=report.pdf" in caplog.text
    assert "pages=14" in caplog.text


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

    assert "converting file=" not in caplog.text


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


def test_pairs_renders_logfmt_in_order_and_skips_none():
    """Lines are read by a person watching the console and by grep afterwards;
    logfmt is what serves both without a legend."""
    pairs = hybrid_server._pairs

    assert pairs(status="ok", dur="34.5s", pages=14) == "status=ok dur=34.5s pages=14"
    assert pairs(a=1, b=None, c=3) == "a=1 c=3", "None means the field is absent"


def test_pairs_quotes_values_that_would_parse_as_two_fields():
    """An unquoted space or '=' would split one field into two, so a filename
    is the obvious way to corrupt a parsed log."""
    pairs = hybrid_server._pairs

    assert pairs(file="my report.pdf") == 'file="my report.pdf"'
    assert pairs(v="a=b") == 'v="a=b"'
    assert pairs(v="") == 'v=""', "an empty value still has to be visible"
    assert pairs(v='say "hi"') == 'v="say \\"hi\\""'


def test_completion_stages_are_ordered_slowest_first():
    """#414 was a request that was almost entirely OCR, reported in an order
    that put layout ahead of it."""
    stages = hybrid_server._stages_slowest_first(
        {
            "layout": {"total_s": 4.1},
            "ocr": {"total_s": 33.8},
            "table_structure": {"total_s": 1.7},
            "page_parse": {"total_s": 0.2},
        }
    )
    assert list(stages) == ["ocr", "layout", "tables", "parse"]
    assert stages["ocr"] == "33.8s"


def test_completion_stages_drop_what_docling_reports_unusably():
    """These values come from docling's profiling objects; a stage that
    reported nothing usable must not cost the request its response."""
    slowest_first = hybrid_server._stages_slowest_first

    assert slowest_first({"ocr": {"total_s": None}}) == {}, "None must not format"
    assert slowest_first({"ocr": "not a dict"}) == {}
    assert slowest_first({"ocr": {}}) == {}
    assert slowest_first({}) == {}
    # A key outside the whitelist is dropped rather than logged: these are
    # splatted as kwargs, where a collision with a fixed key raises TypeError.
    assert slowest_first({"reading_order": {"total_s": 9.0}}) == {}


def test_stage_labels_cannot_collide_with_the_fixed_completion_fields():
    """The whitelist is what stops a docling key from shadowing a kwarg;
    `_pairs(**stages)` would raise TypeError and lose the response."""
    fixed = {"status", "dur", "per_page", "pages", "total"}
    assert not (set(hybrid_server._STAGE_LABELS.values()) & fixed)


def test_pairs_escapes_newlines_that_would_forge_a_log_entry():
    """Quoting does not contain a newline: it still ends the physical line and
    the remainder parses as its own record (CWE-117). page_ranges is caller
    input that reaches a log field directly."""
    pairs = hybrid_server._pairs

    forged = "1-5\n2026-01-01 00:00:00.000 | ERROR | 000000 | forged"
    rendered = pairs(value=forged)

    assert "\n" not in rendered, "one field must stay on one line"
    assert "\\n" in rendered, "the newline is shown, not dropped"
    assert "forged" in rendered, "content is kept, just made harmless"


def test_pairs_escapes_carriage_returns():
    """A lone CR would let a value overwrite the line already printed."""
    rendered = hybrid_server._pairs(value="a\rb")
    assert "\r" not in rendered
    assert rendered == "value=a\\rb"


def test_pairs_escapes_other_control_characters():
    """An ANSI escape runs when an operator views the log."""
    rendered = hybrid_server._pairs(value="a\x1b[31mred")
    assert "\x1b" not in rendered


def test_pairs_keeps_legitimate_unicode():
    """Escaping must not mangle a real filename."""
    assert hybrid_server._pairs(file="한글-보고서.pdf") == "file=한글-보고서.pdf"
    assert hybrid_server._pairs(file="üñîçode.pdf") == "file=üñîçode.pdf"


def test_escaped_output_survives_a_legacy_console_encoding():
    """`logging` drops a record it cannot encode, so a replacement character
    the console cannot write would make a suspicious upload go unlogged --
    the sanitizing turning into a way to hide. Korean Windows is cp949."""
    rendered = hybrid_server._pairs(
        file=hybrid_server._safe_log_name("report\u200b\x1b[31m.pdf")
    )
    for encoding in ("cp949", "cp1252", "ascii"):
        rendered.encode(encoding)  # raises if a record would be dropped


def test_a_real_newline_is_distinguishable_from_a_literal_backslash_n():
    """Both quoted and unquoted: escaping that collapses the two cannot tell
    an injected newline from a filename that merely contains those characters."""
    pairs = hybrid_server._pairs

    # Quoted, because of the space.
    assert pairs(v="a b\nc") != pairs(v="a b\\nc")
    # Unquoted.
    assert pairs(v="a\nc") != pairs(v="a\\nc")

    assert pairs(v="a\nc") == "v=a\\nc"
    assert pairs(v="a\\nc") == "v=a\\\\nc"


def test_exception_messages_are_bounded_before_logging():
    """A parser error carries offsets and data from the uploaded file, with no
    cap on its length, and reaches the log on three separate paths."""
    long_message = "x" * 100_000
    rendered = hybrid_server._pairs(error=hybrid_server._bounded(long_message, 500))
    assert len(rendered) < 600
    assert "100000 chars" in rendered


def test_traceback_lines_cannot_pose_as_records():
    """`_pairs` guards the structured fields, but exc_info text is appended
    after them, and an exception raised over PDF data carries that data in its
    message -- so a failed request could be made to read as a successful one."""
    import io

    forged = "bad xref\n2026-01-01 00:00:00.000 | INFO    | 000000000000 | done status=ok"
    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(
        hybrid_server._IndentTracebacks(
            hybrid_server._LOG_FORMAT, datefmt=hybrid_server._LOG_DATEFMT
        )
    )
    handler.addFilter(hybrid_server._RequestIdFilter())
    logger = logging.getLogger("test.traceback")
    logger.addHandler(handler)
    logger.setLevel(logging.ERROR)
    try:
        try:
            raise ValueError(forged)
        except ValueError:
            logger.error("failed %s", hybrid_server._pairs(status=500), exc_info=True)
    finally:
        logger.removeHandler(handler)

    lines = stream.getvalue().splitlines()
    # Every line after the first is a continuation, so none of them can be
    # read as a record in this module's format.
    assert lines[0].endswith("failed status=500")
    for line in lines[1:]:
        assert line.startswith(hybrid_server._IndentTracebacks._CONTINUATION)
    assert any("Traceback" in line for line in lines), "still a usable traceback"
    assert any("done status=ok" in line for line in lines), "content kept, defanged"


def test_traceback_prefix_survives_another_handler_formatting_first():
    """`Formatter.format` caches its rendered traceback on the record, and
    `configure_logging` deliberately keeps a host's handlers -- so a handler
    that formats first would leave an unprefixed traceback in the cache for
    ours to reuse, undoing the guard."""
    import io

    forged = "bad xref\n2026-01-01 00:00:00.000 | INFO    | 000000000000 | done status=ok"

    host_stream = io.StringIO()
    host = logging.StreamHandler(host_stream)
    host.setFormatter(logging.Formatter("%(message)s"))

    ours_stream = io.StringIO()
    ours = logging.StreamHandler(ours_stream)
    ours.setFormatter(
        hybrid_server._IndentTracebacks(
            hybrid_server._LOG_FORMAT, datefmt=hybrid_server._LOG_DATEFMT
        )
    )
    ours.addFilter(hybrid_server._RequestIdFilter())

    logger = logging.getLogger("test.exc_cache")
    logger.addHandler(host)   # formats first, populating record.exc_text
    logger.addHandler(ours)
    logger.setLevel(logging.ERROR)
    try:
        try:
            raise ValueError(forged)
        except ValueError:
            logger.error("failed %s", hybrid_server._pairs(status=500), exc_info=True)
    finally:
        logger.removeHandler(host)
        logger.removeHandler(ours)

    lines = ours_stream.getvalue().splitlines()
    for line in lines[1:]:
        assert line.startswith(
            hybrid_server._IndentTracebacks._CONTINUATION
        ), f"unprefixed line reused from the cache: {line!r}"

    # The host keeps its own rendering: clearing the cache must not hand our
    # prefix to a handler that never asked for it.
    host_lines = host_stream.getvalue().splitlines()
    assert any(line.startswith("Traceback") for line in host_lines)
    assert not any(
        line.startswith(hybrid_server._IndentTracebacks._CONTINUATION)
        for line in host_lines
    )
