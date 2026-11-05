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
package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.List;

/**
 * Tells whether extracted contents hold any text. A scanned or image-only PDF has none in local
 * mode (#548). Walks the same objects as {@link ContentSanitizer} and stops at the first text.
 */
public final class ExtractedText {

    private ExtractedText() {
    }

    /**
     * @param contents            the contents of each page
     * @param includeHeaderFooter whether page headers and footers go to the output; their text
     *                            counts only then
     * @return true if any object holds text other than whitespace
     */
    public static boolean isPresent(List<List<IObject>> contents, boolean includeHeaderFooter) {
        for (List<IObject> pageContents : contents) {
            if (pageContents == null) {
                continue;
            }
            for (IObject object : pageContents) {
                if (hasText(object, includeHeaderFooter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasText(IObject object, boolean includeHeaderFooter) {
        if (object instanceof SemanticTextNode) {
            return hasText((SemanticTextNode) object);
        } else if (object instanceof TextLine) {
            return hasText((TextLine) object);
        } else if (object instanceof TextChunk) {
            return isText(((TextChunk) object).getValue());
        } else if (object instanceof PDFList) {
            return hasText((PDFList) object, includeHeaderFooter);
        } else if (object instanceof TableBorder) {
            return hasText((TableBorder) object, includeHeaderFooter);
        } else if (object instanceof SemanticHeaderOrFooter) {
            return includeHeaderFooter && hasText(((SemanticHeaderOrFooter) object).getContents(), true);
        }
        return false;
    }

    private static boolean hasText(List<IObject> objects, boolean includeHeaderFooter) {
        for (IObject object : objects) {
            if (hasText(object, includeHeaderFooter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(SemanticTextNode node) {
        for (TextColumn column : node.getColumns()) {
            for (TextBlock block : column.getBlocks()) {
                for (TextLine line : block.getLines()) {
                    if (hasText(line)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasText(TextLine line) {
        return line != null && isText(line.getValue());
    }

    private static boolean hasText(PDFList list, boolean includeHeaderFooter) {
        for (ListItem item : list.getListItems()) {
            for (TextLine line : item.getLines()) {
                if (hasText(line)) {
                    return true;
                }
            }
            if (hasText(item.getContents(), includeHeaderFooter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(TableBorder table, boolean includeHeaderFooter) {
        for (TableBorderRow row : table.getRows()) {
            for (TableBorderCell cell : row.getCells()) {
                if (cell != null && hasText(cell.getContents(), includeHeaderFooter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The output writers drop U+0000, so it does not count as text.
     */
    private static boolean isText(String value) {
        return value != null && !value.replace("\u0000", "").isBlank();
    }
}
