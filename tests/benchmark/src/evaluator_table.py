"""
Most of the code in this file is derived from the paper "Image-based table recognition: data, model, and evaluation".
The original paper can be accessed at: https://arxiv.org/pdf/1911.10683.
The code is available at: https://github.com/ibm-aur-nlp/PubTabNet.
A slight modification has been added to the code to improve the evaluation process.
"""

import re
from collections import deque
from typing import List, Optional, Tuple

from html import unescape
from rapidfuzz.distance import Levenshtein
from lxml import etree, html
from apted.helpers import Tree
from apted import APTED, Config
from bs4 import BeautifulSoup

from converter_markdown_table import convert_to_markdown_with_html_tables


class TableTree(Tree):
    """Light wrapper around ``Tree`` to store table metadata for APTED."""

    def __init__(
        self,
        tag: str,
        colspan: Optional[int] = None,
        rowspan: Optional[int] = None,
        content: Optional[List[str]] = None,
        *children: "TableTree",
    ) -> None:
        self.tag = tag
        self.colspan = colspan
        self.rowspan = rowspan
        self.content = content
        self.children = list(children)

    def bracket(self) -> str:
        """Show tree using brackets notation."""

        if self.tag == "td":
            result = '"tag": %s, "colspan": %d, "rowspan": %d, "text": %s' % (
                self.tag,
                self.colspan,
                self.rowspan,
                self.content,
            )
        else:
            result = '"tag": %s' % self.tag
        for child in self.children:
            result += child.bracket()
        return "{{{}}}".format(result)


class CustomConfig(Config):
    """Custom Configuration for APTED"""

    @staticmethod
    def maximum(*sequences):
        """Get maximum possible value"""
        return max(map(len, sequences))

    def normalized_distance(self, *sequences):
        """Get distance from 0 to 1"""
        return Levenshtein.distance(*sequences) / float(self.maximum(*sequences))

    def rename(self, node1, node2):
        """Compares attributes of trees"""
        if (
            (node1.tag != node2.tag)
            or (node1.colspan != node2.colspan)
            or (node1.rowspan != node2.rowspan)
        ):
            return 1.0
        if node1.tag == "td" and (node1.content or node2.content):
            content1 = "".join(node1.content or [])
            content2 = "".join(node2.content or [])
            normalized_content1 = _normalize(content1)
            normalized_content2 = _normalize(content2)
            if not normalized_content1 and not normalized_content2:
                return 0.0
            return self.normalized_distance(normalized_content1, normalized_content2)
        return 0.0


class TEDSEvaluator(object):
    """Tree Edit Distance basead Similarity"""

    def __init__(self, structure_only=False, n_jobs=1, ignore_nodes=None):
        assert isinstance(n_jobs, int) and (
            n_jobs >= 1
        ), "n_jobs must be an integer greather than 1"
        self.structure_only = structure_only
        self.n_jobs = n_jobs
        self.ignore_nodes = ignore_nodes
        self.__tokens__ = []

    def tokenize(self, node):
        """Tokenizes table cells"""
        self.__tokens__.append("<%s>" % node.tag)
        if node.text is not None:
            self.__tokens__ += list(node.text)
        for n in node.getchildren():
            self.tokenize(n)
        if node.tag != "unk":
            self.__tokens__.append("</%s>" % node.tag)
        if node.tag != "td" and node.tail is not None:
            self.__tokens__ += list(node.tail)

    def load_html_tree(self, node, parent=None):
        """Converts HTML tree to the format required by apted"""
        global __tokens__
        if node.tag == "td":
            if self.structure_only:
                cell = []
            else:
                self.__tokens__ = []
                self.tokenize(node)
                cell = self.__tokens__[1:-1].copy()
            new_node = TableTree(
                node.tag,
                int(node.attrib.get("colspan", "1")),
                int(node.attrib.get("rowspan", "1")),
                cell,
                *deque(),
            )
        else:
            new_node = TableTree(node.tag, None, None, None, *deque())
        if parent is not None:
            parent.children.append(new_node)
        if node.tag != "td":
            for n in node.getchildren():
                self.load_html_tree(n, new_node)
        if parent is None:
            return new_node

    def evaluate(self, pred, true):
        """Computes TEDS score between the prediction and the ground truth of a given sample"""
        if (not pred) or (not true):
            return 0.0
        parser = html.HTMLParser(remove_comments=True, encoding="utf-8")
        pred = html.fromstring(pred, parser=parser)
        true = html.fromstring(true, parser=parser)

        if pred.xpath("body/table") and true.xpath("body/table"):
            pred = pred.xpath("body/table")[0]
            true = true.xpath("body/table")[0]
            _convert_headers_to_cells(pred)
            _convert_headers_to_cells(true)
            if self.ignore_nodes:
                etree.strip_tags(pred, *self.ignore_nodes)
                etree.strip_tags(true, *self.ignore_nodes)
            n_nodes_pred = len(pred.xpath(".//*"))
            n_nodes_true = len(true.xpath(".//*"))
            n_nodes = max(n_nodes_pred, n_nodes_true)
            tree_pred = self.load_html_tree(pred)
            tree_true = self.load_html_tree(true)
            distance = APTED(
                tree_pred, tree_true, CustomConfig()
            ).compute_edit_distance()
            return 1.0 - (float(distance) / n_nodes)
        else:
            return 0.0


def _normalize(text: str) -> str:
    result = unescape(text)
    result = re.sub(r"<br\s*/?>", "\n", result)
    result = re.sub(r"\s+", " ", result).strip()
    return result


def _convert_headers_to_cells(node: etree.Element) -> None:
    for header in node.xpath(".//th"):
        header.tag = "td"


def calc_table_score(
    gt_string: str, pred_string: str, evaluator: TEDSEvaluator
) -> float:
    """Convert edit distance into a similarity score in ``[0.0, 1.0]``."""

    refined_pred = pred_string
    refined_gold = gt_string
    if pred_string.startswith("<table>") and pred_string.endswith("</table>"):
        refined_pred = "<html><body>" + pred_string + "</body></html>"
    elif not pred_string.startswith("<html><body><table>") and not pred_string.endswith(
        "</table></body></html>"
    ):
        refined_pred = "<html><body><table>" + refined_pred + "</table></body></html>"

    if gt_string.startswith("<table>") and gt_string.endswith("</table>"):
        refined_gold = "<html><body>" + gt_string + "</body></html>"
    elif not gt_string.startswith("<html><body><table>") and not gt_string.endswith(
        "</table></body></html>"
    ):
        refined_gold = "<html><body><table>" + refined_gold + "</table></body></html>"

    # remove thead and tbody
    for tok in ["<thead>", "</thead>", "<tbody>", "</tbody>"]:
        refined_pred = refined_pred.replace(tok, "")
        refined_gold = refined_gold.replace(tok, "")

    score = evaluator.evaluate(refined_pred, refined_gold)
    return score


def extract_tables(markdown_with_html: str) -> List[str]:
    tables = []
    soup = BeautifulSoup(markdown_with_html, "html.parser")
    for table in soup.find_all("table"):
        tables.append(str(table))
    return tables


def wrap_tables_in_html(tables: list[str]) -> str:
    body_content = "\n".join(tables)
    return f"<html><body>\n{body_content}\n</body></html>"


def evaluate_table(gt: str, pred: str) -> Tuple[Optional[float], Optional[float]]:
    """Evaluate predicted table markup against ground truth using TEDS metrics.

    Returns ``(None, None)`` when the ground truth does not contain a table.
    """

    gt_with_html = convert_to_markdown_with_html_tables(gt)
    pred_with_html = convert_to_markdown_with_html_tables(pred)

    gt_tables = extract_tables(gt_with_html)
    pred_tables = extract_tables(pred_with_html)

    if not gt_tables:
        return None, None
    if not pred_tables:
        return 0.0, 0.0

    gt_data = wrap_tables_in_html(gt_tables)
    pred_data = wrap_tables_in_html(pred_tables)

    structure_evaluator = TEDSEvaluator(structure_only=True)
    teds_s_score = calc_table_score(gt_data, pred_data, structure_evaluator)

    content_evaluator = TEDSEvaluator(structure_only=False)
    teds_score = calc_table_score(gt_data, pred_data, content_evaluator)

    return teds_score, teds_s_score
