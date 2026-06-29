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
package org.opendataloader.pdf.autotagging;

import org.verapdf.wcag.algorithms.entities.BaseObject;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable, physically-derived object identity for the Accessibility Source spine.
 *
 * <p>The uid is keyed on the content-stream position of an object's
 * earliest-positioned StreamInfo: {@code p{page}:{xobject}:op{minOp}:{minStart}}.
 * {@code operatorIndex} is deterministic across re-parses of the same PDF (it is
 * a token position), so this identity survives the reading-order / grouping
 * changes that shift {@code recognizedStructureId} (a processing-order counter).
 *
 * <p>This is the single source of truth for the uid formula so the core tagger
 * (alt-override lookup) and the pdfua spine emitter (bindings.json) agree
 * byte-for-byte. See docs/design/
 * 2026-06-29-accessibility-source-editor-architecture.md §4.
 */
public final class ContentStreamUid {

    private ContentStreamUid() {
    }

    /**
     * Compute the stable object_uid from a page number and the object's
     * StreamInfos. Returns a {@code p{page}:noinfo} sentinel when there is no
     * StreamInfo (still deterministic for the page).
     */
    public static String compute(Integer page, List<StreamInfo> streamInfos) {
        return compute(page, streamInfos, null);
    }

    /**
     * As {@link #compute(Integer, List)}, but when there is no StreamInfo (e.g. a
     * vector LineArtChunk), fall back to the bbox so multiple bound-less objects
     * on the same page do not collide on a single {@code noinfo} uid. bbox is
     * {@code [x0,y0,x1,y1]} in PDF points (deterministic for a given PDF). The
     * resulting uid is {@code p{page}:noinfo:{x0}_{y0}_{x1}_{y1}}.
     */
    public static String compute(Integer page, List<StreamInfo> streamInfos, double[] bbox) {
        String p = page == null ? "?" : page.toString();
        if (streamInfos == null || streamInfos.isEmpty()) {
            if (bbox != null && bbox.length == 4) {
                return "p" + p + ":noinfo:"
                    + r(bbox[0]) + "_" + r(bbox[1]) + "_" + r(bbox[2]) + "_" + r(bbox[3]);
            }
            return "p" + p + ":noinfo";
        }
        StreamInfo min = null;
        for (StreamInfo si : streamInfos) {
            if (min == null
                    || si.getOperatorIndex() < min.getOperatorIndex()
                    || (si.getOperatorIndex() == min.getOperatorIndex()
                        && si.getStartIndex() < min.getStartIndex())) {
                min = si;
            }
        }
        String xobj = min.getXObjectName() == null ? "_" : min.getXObjectName();
        return "p" + p + ":" + xobj + ":op" + min.getOperatorIndex() + ":" + min.getStartIndex();
    }

    /**
     * Collect ALL StreamInfos an object owns, descending into the type-specific
     * containers the verapdf model uses. A SemanticTextNode keeps its
     * StreamInfos nested in columns → lines → textChunks (not on the node
     * itself), so {@code getStreamInfos()} on the container returns empty; this
     * flattens them. Images and other leaf chunks carry StreamInfos directly.
     *
     * <p>Single source of truth so the spine emitter and any uid lookup see the
     * same content-stream pieces for an object.
     */
    public static List<StreamInfo> collectStreamInfos(IObject obj) {
        List<StreamInfo> out = new ArrayList<>();
        if (obj == null) {
            return out;
        }
        if (obj instanceof SemanticTextNode) {
            for (TextColumn col : ((SemanticTextNode) obj).getColumns()) {
                for (TextLine line : col.getLines()) {
                    for (TextChunk chunk : line.getTextChunks()) {
                        out.addAll(chunk.getStreamInfos());
                    }
                }
            }
            return out;
        }
        if (obj instanceof TableBorder) {
            // A cell's text lives in getContent() as TableTokens (TableToken
            // extends TextChunk → carries StreamInfos directly). getContents()
            // (the IObject list) is usually empty, which made tables bind only
            // ~1 StreamInfo before. Collect both: tokens for cell text, contents
            // for any nested objects.
            for (TableBorderRow row : ((TableBorder) obj).getRows()) {
                for (TableBorderCell cell : row.getCells()) {
                    if (cell == null) {
                        continue;
                    }
                    for (TableToken token : cell.getContent()) {
                        out.addAll(token.getStreamInfos());
                    }
                    for (IObject child : cell.getContents()) {
                        out.addAll(collectStreamInfos(child));
                    }
                }
            }
            return out;
        }
        if (obj instanceof PDFList) {
            // A ListItem extends TextBlock, so its text lives in getLines()
            // (TextLine → TextChunk), NOT in getContents() (which is often
            // empty). Collect both: lines for the item's own text, contents for
            // any nested objects.
            for (ListItem item : ((PDFList) obj).getListItems()) {
                if (item == null) {
                    continue;
                }
                for (TextLine line : item.getLines()) {
                    for (TextChunk chunk : line.getTextChunks()) {
                        out.addAll(chunk.getStreamInfos());
                    }
                }
                for (IObject child : item.getContents()) {
                    out.addAll(collectStreamInfos(child));
                }
            }
            return out;
        }
        // Formula (core SemanticFormula) and other leaf chunks carry StreamInfos
        // directly on the BaseObject.
        if (obj instanceof BaseObject) {
            out.addAll(((BaseObject) obj).getStreamInfos());
        }
        return out;
    }

    /** Convenience: stable uid from an object, collecting its StreamInfos and
     *  falling back to bbox when there is none (e.g. vector LineArtChunk). */
    public static String compute(IObject obj) {
        if (obj == null) {
            return compute(null, null, null);
        }
        double[] bbox = {obj.getLeftX(), obj.getBottomY(), obj.getRightX(), obj.getTopY()};
        return compute(obj.getPageNumber(), collectStreamInfos(obj), bbox);
    }

    /** Round a PDF-point coordinate to a stable integer string for the uid. */
    private static String r(double v) {
        return Long.toString(Math.round(v));
    }
}
