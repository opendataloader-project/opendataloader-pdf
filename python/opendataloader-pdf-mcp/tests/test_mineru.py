"""Unit tests for MinerURunner."""
import subprocess
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from opendataloader_pdf_mcp.mineru import MinerUError, MinerUResult, MinerURunner


@pytest.fixture
def pdf_path(tmp_path):
    p = tmp_path / "doc.pdf"
    p.write_bytes(b"%PDF-1.4")
    return p


class TestMinerURunner:
    def test_run_success(self, pdf_path, tmp_path):
        md_file = tmp_path / "doc.md"
        json_file = tmp_path / "doc.json"
        md_file.write_text("# Extracted", encoding="utf-8")
        json_file.write_text('{"pages": []}', encoding="utf-8")

        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stderr = ""

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                result = MinerURunner().run(pdf_path, tmp_path)

        assert isinstance(result, MinerUResult)
        assert result.markdown == "# Extracted"
        assert result.json_str == '{"pages": []}'
        assert result.exit_code == 0

    def test_run_not_in_path(self, pdf_path, tmp_path):
        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value=None):
            with pytest.raises(MinerUError, match="not found in PATH"):
                MinerURunner().run(pdf_path, tmp_path)

    def test_run_nonzero_exit(self, pdf_path, tmp_path):
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_result.stderr = "fatal: cannot open file"

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                with pytest.raises(MinerUError, match="exited 1"):
                    MinerURunner().run(pdf_path, tmp_path)

    def test_run_missing_output(self, pdf_path, tmp_path):
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stderr = ""

        with patch("opendataloader_pdf_mcp.mineru.shutil.which", return_value="/usr/bin/mineru"):
            with patch("opendataloader_pdf_mcp.mineru.subprocess.run", return_value=mock_result):
                with pytest.raises(MinerUError, match="no output"):
                    MinerURunner().run(pdf_path, tmp_path)
