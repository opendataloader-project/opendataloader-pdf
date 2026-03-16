from __future__ import annotations

import re
import subprocess
from pathlib import Path
from typing import Iterable, List, Sequence


def repair_markdown_outputs(
    input_path: str | List[str] | None,
    output_dir: str | None,
    format_value: str | List[str] | None,
) -> None:
    if input_path is None:
        return

    formats = _normalize_formats(format_value)
    if "markdown" not in formats:
        return

    for pdf_path in _iter_pdf_paths(input_path):
        markdown_path = _resolve_markdown_path(pdf_path, output_dir)
        if not markdown_path.exists():
            continue
        if not _looks_like_collapsed_markdown(markdown_path):
            continue

        rebuilt = _rebuild_markdown_from_layout(pdf_path)
        if rebuilt:
            markdown_path.write_text(rebuilt, encoding="utf-8")


def _normalize_formats(format_value: str | List[str] | None) -> set[str]:
    if format_value is None:
        return {"json"}
    if isinstance(format_value, str):
        return {item.strip() for item in format_value.split(",") if item.strip()}

    normalized: set[str] = set()
    for item in format_value:
        normalized.update(part.strip() for part in str(item).split(",") if part.strip())
    return normalized


def _iter_pdf_paths(input_path: str | List[str]) -> Iterable[Path]:
    paths = input_path if isinstance(input_path, list) else [input_path]
    for raw_path in paths:
        path = Path(raw_path)
        if path.is_dir():
            yield from sorted(path.rglob("*.pdf"))
        elif path.suffix.lower() == ".pdf":
            yield path


def _resolve_markdown_path(pdf_path: Path, output_dir: str | None) -> Path:
    base_dir = Path(output_dir) if output_dir else pdf_path.parent
    return base_dir / f"{pdf_path.stem}.md"


def _looks_like_collapsed_markdown(markdown_path: Path) -> bool:
    content = markdown_path.read_text(encoding="utf-8", errors="ignore")
    return any(
        line.count("<br>") >= 8 and line.count("|") >= 5
        for line in content.splitlines()[:80]
    )


def _rebuild_markdown_from_layout(pdf_path: Path) -> str | None:
    try:
        result = subprocess.run(
            ["pdftotext", "-layout", str(pdf_path), "-"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None

    header = _find_header(result.stdout)
    if header is None:
        return None

    title, columns = header
    rows = _parse_rows(result.stdout, columns)
    if not rows:
        return None

    lines = [
        f"# {title}",
        "",
        "| Voce | " + " | ".join(columns) + " |",
        "| --- | " + " | ".join("---:" for _ in columns) + " |",
    ]
    for label, values in rows:
        lines.append(f"| {label} | " + " | ".join(values) + " |")
    lines.append("")
    return "\n".join(lines)


def _find_header(text: str) -> tuple[str, List[str]] | None:
    for raw_line in text.splitlines():
        line = raw_line.replace("\f", "").strip()
        if not line:
            continue

        parts = re.split(r"\s{2,}", line)
        if len(parts) < 3:
            continue

        columns = parts[1:]
        if not any(re.search(r"\b20\d{2}\b", column) for column in columns):
            continue
        if any(len(column) > 24 for column in columns):
            continue

        return parts[0], columns
    return None


def _parse_rows(text: str, columns: Sequence[str]) -> list[tuple[str, List[str]]]:
    rows: list[tuple[str, List[str]]] = []
    expected_columns = list(columns)

    for raw_line in text.splitlines():
        line = raw_line.replace("\f", "").rstrip()
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("Bilancio - "):
            continue
        if stripped.startswith("www."):
            continue

        parts = re.split(r"\s{2,}", stripped)
        if len(parts) >= 3 and parts[1:] == expected_columns:
            continue

        indent = re.match(r"^\s*", line).group(0)
        label = _format_label(indent + parts[0])
        values = parts[1:]

        row_values = [""] * len(expected_columns)
        if values:
            aligned = values[-len(expected_columns) :]
            row_values[-len(aligned) :] = aligned

        rows.append((label, row_values))

    return rows


def _format_label(label: str) -> str:
    escaped = label.replace("|", r"\|")
    leading_spaces = len(escaped) - len(escaped.lstrip(" "))
    content = escaped.lstrip(" ")
    if content.startswith("TOTALE "):
        leading_spaces = 0
    else:
        leading_spaces = min(leading_spaces, 6)
    return f"{'&nbsp;' * leading_spaces}{content}" if leading_spaces else content
