"""Tests for the UTF-8 mode re-exec guard (#673).

On a non-UTF-8-locale platform (e.g. cp949 on Korean Windows), docling opens
its bundled model config without an explicit ``encoding=`` and crashes with a
UnicodeDecodeError while loading the layout model. Python's UTF-8 mode (PEP
540) fixes this for docling too by changing the default text encoding, but it
can only be enabled at interpreter startup - so the hybrid server must detect
it isn't already active and re-exec itself with ``-X utf8`` before any
dependency import.
"""

import os
import subprocess
import sys
from types import SimpleNamespace
from unittest.mock import patch

from opendataloader_pdf import hybrid_server
from opendataloader_pdf.hybrid_server import _is_utf8_encoding_name, _reexec_with_utf8_if_needed


class TestIsUtf8EncodingName:
    def test_canonical_name_matches(self):
        assert _is_utf8_encoding_name("UTF-8") is True
        assert _is_utf8_encoding_name("utf8") is True
        assert _is_utf8_encoding_name("utf_8") is True

    def test_windows_cp65001_alias_matches(self):
        """cp65001 is Windows' ANSI-codepage name for UTF-8 (codepage 65001,
        e.g. from the "Beta: Use Unicode UTF-8" system setting). A naive
        string comparison against "utf-8" misses it; codec canonicalization
        must not."""
        assert _is_utf8_encoding_name("cp65001") is True
        assert _is_utf8_encoding_name("CP65001") is True

    def test_non_utf8_encoding_does_not_match(self):
        assert _is_utf8_encoding_name("cp949") is False
        assert _is_utf8_encoding_name("ANSI_X3.4-1968") is False

    def test_unknown_encoding_name_does_not_match(self):
        """An unrecognized encoding name must not raise - codecs.lookup can
        LookupError on garbage input."""
        assert _is_utf8_encoding_name("not-a-real-encoding") is False


class TestReexecWithUtf8IfNeeded:
    def test_noop_when_utf8_mode_already_active(self, monkeypatch):
        """No re-exec when the interpreter is already running in UTF-8 mode."""
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=1),
        )
        with patch("opendataloader_pdf.hybrid_server.os.execv") as mock_execv:
            _reexec_with_utf8_if_needed()
        mock_execv.assert_not_called()

    def test_noop_when_locale_already_utf8(self, monkeypatch):
        """No re-exec when sys.flags.utf8_mode is 0 but the locale's preferred
        encoding is already UTF-8 (true on most Linux/macOS deployments) -
        open() already defaults to UTF-8 there without forcing UTF-8 mode, so
        re-execing would only add an unnecessary restart on every startup."""
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=0),
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.locale.getpreferredencoding",
            lambda do_setlocale=True: "UTF-8",
        )
        with patch("opendataloader_pdf.hybrid_server.os.execv") as mock_execv:
            _reexec_with_utf8_if_needed()
        mock_execv.assert_not_called()

    def test_noop_when_locale_is_utf8_alias(self, monkeypatch):
        """No re-exec when the locale reports a UTF-8 alias like cp65001
        (Windows codepage 65001) rather than the canonical "UTF-8" string -
        regression test for the naive-string-comparison bug this replaced."""
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=0),
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.locale.getpreferredencoding",
            lambda do_setlocale=True: "cp65001",
        )
        with patch("opendataloader_pdf.hybrid_server.os.execv") as mock_execv:
            _reexec_with_utf8_if_needed()
        mock_execv.assert_not_called()

    def test_reexecs_with_utf8_flag_when_not_active(self, monkeypatch):
        """Re-exec with -X utf8, by file path, when UTF-8 mode is inactive and
        the locale isn't UTF-8 either (e.g. cp949 on Korean Windows).

        Re-exec targets __file__ rather than `-m opendataloader_pdf.hybrid_server`
        - the `-m` form requires the package to be import-resolvable, which
        breaks running this file directly from an uninstalled checkout.
        """
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=0),
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.locale.getpreferredencoding",
            lambda do_setlocale=True: "cp949",
        )
        monkeypatch.setattr("opendataloader_pdf.hybrid_server.sys.executable", "/usr/bin/python3")
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.argv",
            ["opendataloader-pdf-hybrid", "--port", "5002", "--ocr-lang", "ko"],
        )
        with patch("opendataloader_pdf.hybrid_server.os.execv") as mock_execv:
            _reexec_with_utf8_if_needed()

        mock_execv.assert_called_once()
        program, argv = mock_execv.call_args[0]
        assert program == "/usr/bin/python3"
        assert argv[0] == "/usr/bin/python3"
        assert "-X" in argv
        assert "utf8" in argv
        assert "-m" not in argv
        assert hybrid_server.__file__ in argv

    def test_original_arguments_preserved_and_in_order(self, monkeypatch):
        """Original CLI args are preserved verbatim, in order, at the argv tail."""
        original_args = ["--port", "5003", "--force-ocr", "--device", "cpu"]
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=0),
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.locale.getpreferredencoding",
            lambda do_setlocale=True: "cp949",
        )
        monkeypatch.setattr("opendataloader_pdf.hybrid_server.sys.executable", "/usr/bin/python3")
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.argv",
            ["opendataloader-pdf-hybrid"] + original_args,
        )
        with patch("opendataloader_pdf.hybrid_server.os.execv") as mock_execv:
            _reexec_with_utf8_if_needed()

        _, argv = mock_execv.call_args[0]
        assert argv[-len(original_args) :] == original_args

    def test_execv_failure_is_logged_not_raised(self, monkeypatch, caplog):
        """A failed os.execv (e.g. exec permission denied) must not crash the
        process with a raw traceback - log a clear error and continue instead."""
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.sys.flags",
            SimpleNamespace(utf8_mode=0),
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.locale.getpreferredencoding",
            lambda do_setlocale=True: "cp949",
        )
        monkeypatch.setattr(
            "opendataloader_pdf.hybrid_server.os.execv",
            lambda *a, **k: (_ for _ in ()).throw(OSError("Exec format error")),
        )

        caplog.set_level("ERROR", logger=hybrid_server.logger.name)
        _reexec_with_utf8_if_needed()  # must not raise

        errors = [r.message for r in caplog.records if r.levelname == "ERROR"]
        assert any("Failed to re-exec" in e for e in errors), errors

    def test_main_calls_reexec_guard_before_dependency_check(self, monkeypatch):
        """main() invokes the UTF-8 guard before _check_dependencies()."""
        call_order = []
        monkeypatch.setattr(
            hybrid_server,
            "_reexec_with_utf8_if_needed",
            lambda: call_order.append("reexec_guard"),
        )

        def _stop_after_check_dependencies():
            call_order.append("check_dependencies")
            raise SystemExit(0)

        monkeypatch.setattr(hybrid_server, "_check_dependencies", _stop_after_check_dependencies)

        try:
            hybrid_server.main()
        except SystemExit:
            pass

        assert call_order == ["reexec_guard", "check_dependencies"]


class TestReexecEndToEnd:
    """Real-subprocess proof that the guard's re-exec mechanism actually works,
    beyond the mocked-``os.execv`` unit tests above.

    Runs the real file (not `-m`, not the console-script entry point) from an
    environment with no PYTHONUTF8/-X utf8 already active, mirroring the exact
    "uninstalled checkout" invocation shape the __file__-based re-exec is meant
    to preserve (see #673 and the ModuleNotFoundError regression this design
    avoids: re-exec-by-`-m` breaks when the package isn't import-resolvable).
    """

    def test_direct_script_invocation_survives_reexec_without_module_error(self):
        """Running the file directly must re-exec cleanly, not crash with
        ModuleNotFoundError - the exact regression `-m`-based re-exec caused.

        Forces a genuinely non-UTF-8-mode, non-UTF-8-locale environment so
        the guard actually takes the re-exec branch here, regardless of this
        host's own locale. LC_ALL=C alone is NOT sufficient: CPython
        auto-enables UTF-8 mode as a legacy-C-locale fallback whenever the
        locale resolves to POSIX C/POSIX, *unless* PYTHONUTF8 is explicitly
        forced to 0 - verified empirically: `LC_ALL=C` alone yields
        sys.flags.utf8_mode == 1 on this dev environment, which would make
        this test's assertions below pass trivially without ever exercising
        the re-exec branch. PYTHONCOERCECLOCALE=0 additionally disables PEP
        538's locale-coercion attempt, for defense in depth across platforms.
        """
        env = dict(os.environ)
        env["LC_ALL"] = "C"
        env["PYTHONUTF8"] = "0"
        env["PYTHONCOERCECLOCALE"] = "0"

        # Self-verifying: prove the forced environment actually produces a
        # non-UTF-8-mode, non-UTF-8-locale process *before* trusting the main
        # assertions below. Without this, a future Python/OS change that
        # silently restores UTF-8 mode under this env would make the test
        # pass for the wrong reason again - the exact failure mode found in
        # review.
        probe = subprocess.run(
            [sys.executable, "-c", "import locale, sys; "
             "print(sys.flags.utf8_mode); "
             "print(locale.getpreferredencoding(False))"],
            env=env,
            capture_output=True,
            text=True,
            timeout=10,
        )
        probe_lines = probe.stdout.splitlines()
        assert probe.returncode == 0 and len(probe_lines) >= 2, (
            probe.returncode, probe.stdout, probe.stderr,
        )
        probe_utf8_mode, probe_encoding = probe_lines[0].strip(), probe_lines[1].strip()
        assert probe_utf8_mode == "0", (
            "test environment failed to force sys.flags.utf8_mode == 0 "
            f"(got {probe_utf8_mode!r}); this test would not exercise the "
            "re-exec branch at all"
        )
        assert not _is_utf8_encoding_name(probe_encoding), (
            "test environment failed to force a non-UTF-8 locale "
            f"(getpreferredencoding returned {probe_encoding!r}); this test "
            "would not exercise the re-exec branch at all"
        )

        result = subprocess.run(
            [sys.executable, hybrid_server.__file__, "--help"],
            env=env,
            capture_output=True,
            text=True,
            timeout=30,
        )

        assert "ModuleNotFoundError" not in result.stderr, result.stderr
        assert "No module named 'opendataloader_pdf'" not in result.stderr, result.stderr
        # Whether or not [hybrid] deps are installed in this environment, the
        # process must reach main()'s own dependency-check/argument-parsing
        # logic - never die on package resolution before getting there.
        assert (
            "Missing dependencies" in result.stderr
            or "Missing dependencies" in result.stdout
            or result.returncode == 0
        ), (result.returncode, result.stdout, result.stderr)
