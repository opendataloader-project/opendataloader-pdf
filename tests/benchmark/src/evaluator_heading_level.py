"""Evaluate structural similarity between two Markdown documents.

This module extracts headings (e.g. ``#``, ``##``) and non-heading blocks
from a pair of Markdown strings, builds a flat section tree that treats all
heading levels as equivalent, and then computes two scores:

* ``MHS``  – Markdown Hierarchical Similarity with textual content.
* ``MHS-S`` – Markdown Hierarchical Similarity (Structure only).

When computing text similarity, Markdown and HTML table blocks are excluded
to avoid penalising structural matches where cell content is irrelevant to
the metric.
"""

from __future__ import annotations

import re
from typing import List, Optional, Tuple

from rapidfuzz.distance import Levenshtein
from apted import APTED, Config
from apted.helpers import Tree

from converter_markdown_table import convert_to_markdown_with_html_tables

_HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*)$")


def _normalize_text(text: str) -> str:
    """Collapse repeated whitespace and strip leading/trailing spaces."""

    return re.sub(r"\s+", " ", text).strip()


class HeadingTree(Tree):
    """Simple tree container for heading/content nodes."""

    def __init__(
        self, tag: str, text: Optional[str] = None, *children: "HeadingTree"
    ) -> None:
        self.tag = tag
        self.text = text
        self.children = list(children)


class HeadingConfig(Config):
    """Configure APTED to compare heading/content nodes."""

    def __init__(self, include_text: bool) -> None:
        self.include_text = include_text

    @staticmethod
    def _normalized_distance(text_a: str, text_b: str) -> float:
        if not text_a and not text_b:
            return 0.0
        length = max(len(text_a), len(text_b), 1)
        return Levenshtein.distance(text_a, text_b) / float(length)

    def rename(self, node1: HeadingTree, node2: HeadingTree) -> float:
        if node1.tag != node2.tag:
            return 1.0
        if not self.include_text:
            return 0.0
        return self._normalized_distance(node1.text or "", node2.text or "")


def _flush_content(content_lines: List[str], parent: HeadingTree) -> None:
    """Append a content node built from ``content_lines`` to the tree."""

    if not content_lines:
        return
    content_text = _normalize_text(" ".join(content_lines))
    if not content_text:
        content_lines.clear()
        return
    content_node = HeadingTree("content", content_text)
    parent.children.append(content_node)
    content_lines.clear()


def _parse_markdown_structure(markdown: Optional[str]) -> HeadingTree:
    """Parse Markdown into a tree that groups content under nearest heading."""

    root = HeadingTree("document")
    if not markdown:
        return root

    current_container = root
    pending_lines: List[str] = []
    lines = markdown.splitlines()
    idx = 0

    while idx < len(lines):
        raw_line = lines[idx]
        match = _HEADING_PATTERN.match(raw_line)
        if match:
            _flush_content(pending_lines, current_container)
            heading_text = _normalize_text(match.group(2))
            heading_node = HeadingTree("heading", heading_text)
            root.children.append(heading_node)
            current_container = heading_node
            idx += 1
            continue

        normalized = _normalize_text(raw_line)
        if normalized:
            pending_lines.append(normalized)
        idx += 1

    _flush_content(pending_lines, current_container)
    return root


def _count_nodes(node: HeadingTree) -> int:
    return 1 + sum(_count_nodes(child) for child in node.children)


def _compute_edit_distance(
    tree_a: HeadingTree, tree_b: HeadingTree, include_text: bool
) -> float:
    config = HeadingConfig(include_text=include_text)
    return float(APTED(tree_a, tree_b, config).compute_edit_distance())


def evaluate_heading_level(
    gt: Optional[str], pred: Optional[str]
) -> Tuple[Optional[float], Optional[float]]:
    """Return ``(MHS, MHS-S)`` similarity scores in ``[0.0, 1.0]``.

    Returns ``(None, None)`` when the ground truth lacks any heading nodes.
    Returns ``(0.0, 0.0)`` when headings exist in the ground truth but not in
    the prediction.
    """
    gt_with_html = convert_to_markdown_with_html_tables(gt)
    pred_with_html = convert_to_markdown_with_html_tables(pred)

    gt_tree = _parse_markdown_structure(gt_with_html)
    if not any(child.tag == "heading" for child in gt_tree.children):
        return None, None

    pred_tree = _parse_markdown_structure(pred_with_html)
    if not any(child.tag == "heading" for child in pred_tree.children):
        return 0.0, 0.0

    max_nodes = max(_count_nodes(gt_tree), _count_nodes(pred_tree), 1)

    edit_with_text = _compute_edit_distance(gt_tree, pred_tree, include_text=True)
    edit_structure_only = _compute_edit_distance(gt_tree, pred_tree, include_text=False)

    mhs = 1.0 - (edit_with_text / max_nodes)
    mhs_s = 1.0 - (edit_structure_only / max_nodes)

    mhs = max(0.0, min(1.0, mhs))
    mhs_s = max(0.0, min(1.0, mhs_s))
    return mhs, mhs_s
