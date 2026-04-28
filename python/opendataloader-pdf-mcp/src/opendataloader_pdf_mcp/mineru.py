"""MinerU subprocess wrapper."""
from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

_MINERU_TIMEOUT = 300


class MinerUError(Exception):
    pass


@dataclass
class MinerUResult:
    markdown: str
    json_str: str
    exit_code: int
    stderr: str


class MinerURunner:
    def run(self, pdf_path: Path, output_dir: Path) -> MinerUResult:
        if shutil.which("mineru") is None:
            raise MinerUError("mineru not found in PATH")

        try:
            result = subprocess.run(
                ["mineru", "-p", str(pdf_path), "-o", str(output_dir), "--method", "auto"],
                capture_output=True,
                text=True,
                timeout=_MINERU_TIMEOUT,
            )
        except subprocess.TimeoutExpired:
            raise MinerUError(f"mineru timed out after {_MINERU_TIMEOUT}s")

        if result.returncode != 0:
            raise MinerUError(f"mineru exited {result.returncode}: {result.stderr[:500]}")

        # MinerU 2.x writes output into {output_dir}/{stem}/ subdirectory
        sub = output_dir / pdf_path.stem
        md_file = sub / f"{pdf_path.stem}.md"
        json_file = sub / "content_list.json"

        if not md_file.is_file() or not json_file.is_file():
            raise MinerUError("mineru produced no output")

        return MinerUResult(
            markdown=md_file.read_text(encoding="utf-8"),
            json_str=json_file.read_text(encoding="utf-8"),
            exit_code=result.returncode,
            stderr=result.stderr,
        )
