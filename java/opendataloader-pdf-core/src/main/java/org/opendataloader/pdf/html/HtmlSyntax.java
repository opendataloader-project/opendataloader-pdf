/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

/**
 * Constants for HTML syntax elements used in HTML output generation.
 */
public class HtmlSyntax {
    /** Format string for image file names. */
    public static final String IMAGE_FILE_NAME_FORMAT = "figure%d.png";
    /** Line break character for HTML output. */
    public static final String HTML_LINE_BREAK = "\n";
    /** Opening table tag with border. */
    public static final String HTML_TABLE_TAG = "<table border=\"1\">";
    /** Closing table tag. */
    public static final String HTML_TABLE_CLOSE_TAG = "</table>";
    /** Opening table row tag. */
    public static final String HTML_TABLE_ROW_TAG = "<tr>";
    /** Closing table row tag. */
    public static final String HTML_TABLE_ROW_CLOSE_TAG = "</tr>";
    /** Opening table cell tag. */
    public static final String HTML_TABLE_CELL_TAG = "<td>";
    /** Closing table cell tag. */
    public static final String HTML_TABLE_CELL_CLOSE_TAG = "</td>";
    /** Opening table header cell tag. */
    public static final String HTML_TABLE_HEADER_TAG = "<th>";
    /** Closing table header cell tag. */
    public static final String HTML_TABLE_HEADER_CLOSE_TAG = "</th>";
    /** Opening ordered list tag. */
    public static final String HTML_ORDERED_LIST_TAG = "<ol>";
    /** Closing ordered list tag. */
    public static final String HTML_ORDERED_LIST_CLOSE_TAG = "</ol>";
    /** Opening unordered list tag. */
    public static final String HTML_UNORDERED_LIST_TAG = "<ul>";
    /** Closing unordered list tag. */
    public static final String HTML_UNORDERED_LIST_CLOSE_TAG = "</ul>";
    /** Opening list item tag. */
    public static final String HTML_LIST_ITEM_TAG = "<li>";
    /** Closing list item tag. */
    public static final String HTML_LIST_ITEM_CLOSE_TAG = "</li>";
    /** HTML line break tag. */
    public static final String HTML_LINE_BREAK_TAG = "<br>";
    /** Indentation string for paragraphs. */
    public static final String HTML_INDENT = "";
    /** Opening paragraph tag. */
    public static final String HTML_PARAGRAPH_TAG = "<p>";
    /** Closing paragraph tag. */
    public static final String HTML_PARAGRAPH_CLOSE_TAG = "</p>";
    /** Opening figure caption tag. */
    public static final String HTML_FIGURE_CAPTION_TAG = "<figcaption>";
    /** Closing figure caption tag. */
    public static final String HTML_FIGURE_CAPTION_CLOSE_TAG = "</figcaption>";
    /** Opening math display block tag for MathJax/KaTeX rendering. */
    public static final String HTML_MATH_DISPLAY_TAG = "<div class=\"math-display\">";
    /** Closing math display block tag. */
    public static final String HTML_MATH_DISPLAY_CLOSE_TAG = "</div>";
}
