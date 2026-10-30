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
from opendataloader_pdf.hybrid_server import _reexec_with_utf8_if_needed


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

        Forces LC_ALL=C (a genuinely non-UTF-8 locale) so the guard actually
        takes the re-exec branch here, regardless of this host's own locale."""
        env = dict(os.environ)
        env.pop("PYTHONUTF8", None)
        env["LC_ALL"] = "C"

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
