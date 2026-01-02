"""Utilities for converting Markdown tables into HTML snippets."""

from html import escape as html_escape
from typing import List, Optional


def _split_markdown_row(line: str) -> Optional[List[str]]:
    """Split a Markdown table row into stripped cell strings."""

    trimmed = line.strip()
    if not trimmed or "|" not in trimmed:
        return None
    cells = trimmed.split("|")
    if trimmed.startswith("|"):
        cells = cells[1:]
    if cells and trimmed.endswith("|"):
        cells = cells[:-1]
    cells = [cell.strip() for cell in cells]
    if not cells:
        return None
    return cells


def _is_separator_row(cells: List[str]) -> bool:
    """Check whether ``cells`` contains a Markdown header separator row."""

    if not cells:
        return False
    for cell in cells:
        content = cell.replace(" ", "")
        if not content:
            return False
        if not set(content) <= {"-", ":"}:
            return False
    return True


def _markdown_table_to_html(header: List[str], rows: List[List[str]]) -> str:
    """Render a simple Markdown table into HTML suitable for TEDS evaluation."""

    header_html = (
        "<tr>" + "".join(f"<th>{html_escape(cell)}</th>" for cell in header) + "</tr>"
    )
    row_html = "".join(
        "<tr>" + "".join(f"<td>{html_escape(cell)}</td>" for cell in row) + "</tr>"
        for row in rows
    )
    return f"<table>{header_html}{row_html}</table>"


def _normalize_cells(cells: List[str], target_width: int) -> List[str]:
    """Best-effort normalization for slightly malformed Markdown rows."""

    if target_width <= 0:
        return cells
    if len(cells) == target_width:
        return cells
    if len(cells) == 3 and target_width > 3:
        # Recover common "colspan" style headers like ``| A | B | C |`` when the
        # middle header should span multiple columns.
        return [cells[0]] + [cells[1]] * (target_width - 2) + [cells[2]]
    if len(cells) < target_width:
        return cells + [""] * (target_width - len(cells))
    return cells[:target_width]


def convert_to_markdown_with_html_tables(markdown: str) -> str:
    """Convert Markdown tables in ``markdown`` into HTML tables."""

    if not markdown:
        return markdown

    lines = markdown.splitlines(keepends=True)
    if not lines:
        return markdown

    converted_segments: List[str] = []
    idx = 0
    while idx < len(lines):
        header_cells = _split_markdown_row(lines[idx])
        if not header_cells or idx + 1 >= len(lines):
            converted_segments.append(lines[idx])
            idx += 1
            continue

        separator_cells = _split_markdown_row(lines[idx + 1])
        if not separator_cells or not _is_separator_row(separator_cells):
            converted_segments.append(lines[idx])
            idx += 1
            continue

        target_width = max(len(header_cells), len(separator_cells))
        header_cells = _normalize_cells(header_cells, target_width)

        body_rows: List[List[str]] = []
        walker = idx + 2
        while walker < len(lines):
            row_cells = _split_markdown_row(lines[walker])
            if not row_cells:
                break
            body_rows.append(_normalize_cells(row_cells, target_width))
            walker += 1

        if header_cells and all(cell == "" for cell in header_cells) and body_rows:
            header_cells = body_rows.pop(0)

        html_table = _markdown_table_to_html(header_cells, body_rows)
        line_ending = ""
        segment = lines[idx]
        if segment.endswith("\r\n"):
            line_ending = "\r\n"
        elif segment.endswith("\n"):
            line_ending = "\n"
        elif segment.endswith("\r"):
            line_ending = "\r"
        else:
            line_ending = ""
        converted_segments.append(html_table + line_ending)
        idx = walker

    return "".join(converted_segments)


__all__ = ["convert_to_markdown_with_html_tables"]
