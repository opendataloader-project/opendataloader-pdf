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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSArray;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSDocument;
import org.verapdf.cos.COSIndirect;
import org.verapdf.cos.COSName;
import org.verapdf.cos.COSObject;
import org.verapdf.pd.structure.PDStructElem;
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a Caption sits relative to what it describes.
 *
 * <p>A captioned figure is wrapped in a Sect so the Figure and its Caption are
 * siblings. Nesting the caption inside the figure is legal, but it puts the
 * caption's text in the figure's subtree, where ISO 32000-1 14.9.4 makes the
 * figure's own {@code /Alt} stand in for everything below it. Table and L keep
 * their captions as children — ISO 32000-1 Table 337 permits that, and a table's
 * caption competes with no /Alt.
 *
 * <p>The wrapper cannot be Div, and that is the part worth a test rather than a
 * comment: Div satisfies every rule in the profile's own descriptions, so the
 * mistake is invisible to a reading of the spec. It fails because veraPDF walks
 * past Div when it computes a parent.
 */
class CaptionPlacementTest {

    private static BoundingBox box(double x0, double y0, double x1, double y1) {
        return new BoundingBox(0, x0, y0, x1, y1);
    }

    /** The spatial rule that decides before-or-after, exercised directly. */
    private static boolean captionComesFirst(BoundingBox caption, BoundingBox parent)
            throws Exception {
        Method m = AutoTaggingProcessor.class
                .getDeclaredMethod("isCaptionFirstChild", BoundingBox.class, BoundingBox.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, caption, parent);
    }

    /**
     * The wrapper must not be a tag veraPDF treats as transparent.
     *
     * <p>Div, Part and NonStruct are pass-through: {@code parentStandardType}
     * loops past them, so a Caption inside a Div reports Document as its parent
     * and still fails Table 5 Document-Caption.1. Measured — the tree and every
     * /P pointer were correct and the document failed anyway. Whatever wrapper
     * this code picks has to stay off that list.
     */
    @Test
    void theWrapperIsNotAPassThroughTag() {
        assertFalse(PDStructElem.isPassThroughTag(TaggedPDFConstants.SECT),
                "Sect must remain a real parent for the caption to be inside it");

        assertTrue(PDStructElem.isPassThroughTag(TaggedPDFConstants.DIV),
                "Div is transparent, which is why it cannot be the wrapper");
        assertTrue(PDStructElem.isPassThroughTag(TaggedPDFConstants.PART),
                "Part is transparent for the same reason");
    }

    @Test
    void aCaptionAboveItsFigureIsPlacedBeforeIt() throws Exception {
        // PDF coordinates: y grows upward, so "above" is a larger y.
        BoundingBox figure = box(100, 400, 300, 600);
        BoundingBox caption = box(100, 610, 300, 630);

        assertTrue(captionComesFirst(caption, figure),
                "a caption whose centre sits above the figure's top edge precedes it");
    }

    @Test
    void aCaptionBelowItsFigureIsPlacedAfterIt() throws Exception {
        BoundingBox figure = box(100, 400, 300, 600);
        BoundingBox caption = box(100, 370, 300, 390);

        assertFalse(captionComesFirst(caption, figure),
                "a caption whose centre sits below the figure's bottom edge follows it");
    }

    /**
     * A caption overlapping its figure vertically falls back to horizontal
     * order, so the outcome stays defined rather than depending on which
     * element happened to be built first.
     */
    @Test
    void aCaptionOverlappingItsFigureFallsBackToHorizontalOrder() throws Exception {
        BoundingBox figure = box(300, 400, 500, 600);

        assertTrue(captionComesFirst(box(100, 450, 250, 550), figure),
                "a caption to the left comes first");
        assertFalse(captionComesFirst(box(550, 450, 700, 550), figure),
                "a caption to the right comes last");
    }

    /**
     * A missing box must not throw: the caller has no fallback if it does, and
     * an unplaced caption is dropped from the tree entirely.
     */
    @Test
    void aMissingBoundingBoxDoesNotThrow() throws Exception {
        assertTrue(captionComesFirst(null, box(0, 0, 10, 10)));
        assertTrue(captionComesFirst(box(0, 0, 10, 10), null));
    }

    /**
     * The sibling a caption is placed next to must be found by identity.
     *
     * <p>createCaptionStructElemBeside locates its sibling in the parent's /K
     * array. COSIndirect.equals compares getDirect(), and COSDictionary.equals
     * is a deep key-set-and-values comparison, so two struct elements built the
     * same way compare equal — and two Figures on one page are built the same
     * way, carrying the same /S, /Type and /Pg before their kids are attached.
     *
     * <p>A value search therefore returns the first match rather than the
     * intended element, and the caption is inserted beside the wrong figure.
     * Today's Sect holds only its own figure, so the wrong index happens to be
     * the right one — but that method exists precisely so that adding anything
     * else to the Sect does not move the caption, which is the case where the
     * value search breaks. This pins the contract the fix relies on.
     */
    @Test
    void twoStructElementsBuiltAlikeAreEqualButNotIdentical() {
        COSDocument document = new COSDocument(null);
        COSObject first = structElement(document, TaggedPDFConstants.FIGURE);
        COSObject second = structElement(document, TaggedPDFConstants.FIGURE);

        assertNotSame(first, second, "they are separate elements");
        assertEquals(first, second,
                "and they compare equal, which is why equals cannot locate either");

        COSObject kids = COSArray.construct();
        kids.add(first);
        kids.add(second);

        assertEquals(0, indexOfByValue(kids, second),
                "a value search finds the wrong element");
        assertEquals(1, indexOfByIdentity(kids, second),
                "an identity search finds the intended one");
    }

    /** As addStructElement builds one, minus the parts needing a live document. */
    private static COSObject structElement(COSDocument document, String type) {
        COSObject element = COSIndirect.construct(COSDictionary.construct(), document);
        element.setKey(ASAtom.S, COSName.construct(type));
        element.setKey(ASAtom.TYPE, COSName.construct(ASAtom.STRUCT_ELEM));
        return element;
    }

    private static int indexOfByValue(COSObject kids, COSObject wanted) {
        for (int i = 0; i < kids.size(); i++) {
            if (kids.at(i) != null && kids.at(i).equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfByIdentity(COSObject kids, COSObject wanted) {
        for (int i = 0; i < kids.size(); i++) {
            if (kids.at(i) == wanted) {
                return i;
            }
        }
        return -1;
    }
}
