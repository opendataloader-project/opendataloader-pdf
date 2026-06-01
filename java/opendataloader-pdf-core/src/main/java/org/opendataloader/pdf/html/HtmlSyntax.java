/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    public static final String HTML_TABLE_TAG = "<table border=\"%d\"%s>";
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
    public static final String HTML_UNORDERED_LIST_TAG = "<ul%s>";
    /** Closing unordered list tag. */
    public static final String HTML_UNORDERED_LIST_CLOSE_TAG = "</ul>";
    /** Opening list item tag. */
    public static final String HTML_LIST_ITEM_TAG = "<li%s>";
    /** Closing list item tag. */
    public static final String HTML_LIST_ITEM_CLOSE_TAG = "</li>";
    /** HTML line break tag. */
    public static final String HTML_LINE_BREAK_TAG = "<br>";
    /** Indentation string for paragraphs. */
    public static final String HTML_INDENT = "";
    /** Opening paragraph tag. */
    public static final String HTML_PARAGRAPH_TAG = "<p%s>";
    /** Closing paragraph tag. */
    public static final String HTML_PARAGRAPH_CLOSE_TAG = "</p>";
    /** Opening figure tag. */
    public static final String HTML_FIGURE_TAG = "<figure%s>";
    /** Closing figure tag. */
    public static final String HTML_FIGURE_CLOSE_TAG = "</figure>";
    /** Opening figure caption tag. */
    public static final String HTML_FIGURE_CAPTION_TAG = "<figcaption%s>";
    /** Closing figure caption tag. */
    public static final String HTML_FIGURE_CAPTION_CLOSE_TAG = "</figcaption>";
    /** Opening math display block tag for MathJax/KaTeX rendering. */
    public static final String HTML_MATH_DISPLAY_TAG = "<div class=\"math-display\"%s>";
    /** Closing math display block tag. */
    public static final String HTML_MATH_DISPLAY_CLOSE_TAG = "</div>";
    /** Span opening tag. */
    public static final String HTML_SPAN_START_TAG = "<span%s>";
    /** Style attribute */
    public static final String HTML_STYLE_ATTRIBUTE = " style=\"%s\"";
    /** Span closing tag. */
    public static final String HTML_SPAN_CLOSE_TAG = "</span>";
    /** Text decoration property. */
    public static final String HTML_TEXT_DECORATION_STYLE_PROPERTY = "text-decoration:%s; ";
    /** Strikethrough text property. */
    public static final String HTML_STRIKETHROUGH_VALUE = " line-through";
    /** Underline text property. */
    public static final String HTML_UNDERLINE_VALUE = " underline";
    /** Italic font property. */
    public static final String HTML_ITALIC_STYLE_PROPERTY = "font-style: italic; ";
    /** Font color property. */
    public static final String HTML_FONT_COLOR_PROPERTY = "color: rgb(%d, %d, %d); ";
    /** Font weight property. */
    public static final String HTML_FONT_WEIGHT_PROPERTY = "font-weight: %d; ";
    /** Font size property. */
    public static final String HTML_FONT_SIZE_PROPERTY = "font-size: %spx; ";
    /** Position absolute property */
    public static final String HTML_POSITION_ABSOLUTE_PROPERTY = "position: absolute; ";
    /** Top property */
    public static final String HTML_TOP_PROPERTY = "top: %spx; ";
    /** Left property */
    public static final String HTML_LEFT_PROPERTY = "left: %spx; ";
    /** Margin 0 property */
    public static final String HTML_ZERO_MARGIN_PROPERTY = "margin: 0; ";
    /** Style tag entry */
    public static final String HTML_STYLE_TAG_ENTRY_VALUE = "\t\t%s\n";
    /** Style tag entry opening */
    public static final String HTML_STYLE_TAG_ENTRY_OPENING = "\t%s {\n";
    /** Style tag entry closing*/
    public static final String HTML_STYLE_TAG_ENTRY_CLOSING = "\t}\n";
    /** Default line height property */
    public static final String HTML_DEFAULT_LINE_HEIGHT_PROPERTY = "line-height: 1.0; ";
    /** Line height property */
    public static final String HTML_LINE_HEIGHT_PROPERTY = "line-height: %s; ";
    /** Height property */
    public static final String HTML_HEIGHT_PROPERTY = "height: %spx; ";
    /** Width property */
    public static final String HTML_WIDTH_PROPERTY = "width: %spx; ";
    /** Text alignment property */
    public static final String HTML_TEXT_ALIGNMENT_PROPERTY = "text-align: %s; ";
    /** List style none property */
    public static final String HTML_LIST_STYLE_TYPE_NONE_PROPERTY = "list-style-type: none; ";
    /** Border collapse property */
    public static final String HTML_BORDER_COLLAPSE_PROPERTY = "border-collapse: collapse; ";
    /** Vertical align top property */
    public static final String HTML_VERTICAL_ALIGN_TOP_PROPERTY = "vertical-align: top; ";
    /** Vertical align sub property */
    public static final String HTML_VERTICAL_ALIGN_SUB_PROPERTY = "vertical-align: sub; ";
    /** Vertical align super property */
    public static final String HTML_VERTICAL_ALIGN_SUPER_PROPERTY = "vertical-align: super; ";
    /** Padding 0 property */
    public static final String HTML_PADDING_ZERO_PROPERTY = "padding: 0; ";
    /** Width 100% property */
    public static final String HTML_WIDTH_100_PROPERTY = "width: 100%; ";
    /** Font family property */
    public static final String HTML_FONT_FAMILY_PROPERTY = "font-family: %s, Times New Roman, serif; ";
    /** Default font weight property */
    public static final String HTML_DEFAULT_FONT_WEIGHT_PROPERTY = "font-weight: 400; ";
    /** White space nowrap property */
    public static final String HTML_WHITE_SPACE_PROPERTY = "white-space: nowrap; ";
    /** Transform rotate property */
    public static final String HTML_TRANSFORM_ROTATE_PROPERTY = "transform: rotate(%ddeg); ";
}
