"""Tests for the option coverage check of verification/ci-verify.py (#709).

ci-verify.py has no behavioral check for the options in HYBRID_OPTIONS. The Python wrapper passes
each option of options.json to the CLI JAR, so the coverage check must at least fail when the JAR
does not know one of these options.
"""

import importlib.util
import os
from pathlib import Path
from unittest import mock

import pytest

CI_VERIFY = Path(__file__).resolve().parents[3] / "verification" / "ci-verify.py"

# The layout of the JAR help: options at the left, wrapped descriptions in a column
DESCRIPTION_COLUMN = 38


@pytest.fixture(scope="module")
def ci_verify():
    spec = importlib.util.spec_from_file_location("ci_verify", CI_VERIFY)
    module = importlib.util.module_from_spec(spec)
    # The script sets JAVA_TOOL_OPTIONS and TMPDIR for its child processes when it loads
    with mock.patch.dict(os.environ):
        spec.loader.exec_module(module)
    return module


def _help_text(flags, mention=None):
    """Help text in the layout of the JAR. The descriptions can name another option."""
    lines = ["usage: [options] <INPUT FILE OR FOLDER>...", " Options:"]
    lines.append(" -f,--format <arg>".ljust(DESCRIPTION_COLUMN) + "Output formats")
    for flag in sorted(flags):
        lines.append(f"    {flag} <arg>".ljust(DESCRIPTION_COLUMN) + "Description of the option")
    if mention:
        # As in the real help: "... ignored in --hybrid mode" and a wrapped line "--hybrid-url. ..."
        lines.append("    --threads <arg>".ljust(DESCRIPTION_COLUMN) + f"Ignored in {mention} mode")
        lines.append(" " * DESCRIPTION_COLUMN + f"{mention}. Values: a, b")
    return "\n".join(lines) + "\n"


def _exit_code(check):
    try:
        check()
    except SystemExit as exc:
        return exc.code
    return 0


def test_option_coverage_fails_when_a_hybrid_option_is_absent_from_the_cli_help(
    ci_verify, monkeypatch
):
    passed = []
    for absent in sorted(ci_verify.HYBRID_OPTIONS):
        help_text = _help_text(ci_verify.HYBRID_OPTIONS - {absent}, mention=absent)
        monkeypatch.setattr(ci_verify, "_cli_help_text", lambda: help_text, raising=False)
        if _exit_code(ci_verify.check_option_coverage) != 1:
            passed.append(absent)
    assert (
        not passed
    ), f"the option coverage check passed without these options in the CLI help: {passed}"


def test_option_coverage_fails_when_the_cli_help_cannot_be_read(ci_verify, monkeypatch):
    monkeypatch.setattr(ci_verify, "_cli_help_text", lambda: None, raising=False)
    assert (
        _exit_code(ci_verify.check_option_coverage) == 1
    ), "the option coverage check passed without the CLI help"


def test_option_coverage_passes_when_each_hybrid_option_is_in_the_cli_help(ci_verify, monkeypatch):
    help_text = _help_text(ci_verify.HYBRID_OPTIONS)
    monkeypatch.setattr(ci_verify, "_cli_help_text", lambda: help_text, raising=False)
    assert _exit_code(ci_verify.check_option_coverage) == 0


def test_option_coverage_passes_with_the_bundled_cli(ci_verify):
    assert _exit_code(ci_verify.check_option_coverage) == 0
