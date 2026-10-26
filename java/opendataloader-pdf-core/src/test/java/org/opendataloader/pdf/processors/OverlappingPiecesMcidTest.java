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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObject;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One MCID for overlapping pieces of one string in one element.
 *
 * <p>ChunksWriter writes one marked-content sequence per piece and cannot nest
 * one in another. Two overlapping pieces got two MCIDs and one sequence, so the
 * other MCID pointed at nothing. Measured on a scan's OCR layer, where the
 * pieces came as [1,2) and then [0,5), each in its own call.
 */
class OverlappingPiecesMcidTest {

    @BeforeEach
    void clearState() throws Exception {
        for (String name : List.of("structParents", "operatorIndexesToStreamInfosMap")) {
            Field field = AutoTaggingProcessor.class.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();
        }
    }

    private static void mark(COSObject parent, StreamInfo... pieces) throws Exception {
        Method method = AutoTaggingProcessor.class.getDeclaredMethod("addMcidChildren",
                List.class, Integer.class, COSObject.class);
        method.setAccessible(true);
        method.invoke(null, new ArrayList<>(List.of(pieces)), 0, parent);
    }

    @Test
    void overlappingPiecesInOneElementShareOneMcid() throws Exception {
        COSObject paragraph = COSDictionary.construct();
        StreamInfo inner = new StreamInfo(3005, null, 1, 2);
        StreamInfo outer = new StreamInfo(3005, null, 0, 5);

        mark(paragraph, inner);
        mark(paragraph, outer);

        assertEquals(1, paragraph.getKey(ASAtom.K).size(), "one MCID, not one per piece");
        assertEquals(0, inner.getStartIndex());
        assertEquals(5, inner.getEndIndex());
        assertEquals(inner.getMcid(), outer.getMcid());
    }

    @Test
    void piecesThatDoNotOverlapKeepTheirOwnMcids() throws Exception {
        COSObject paragraph = COSDictionary.construct();

        mark(paragraph, new StreamInfo(3005, null, 0, 2));
        mark(paragraph, new StreamInfo(3005, null, 2, 5));

        assertEquals(2, paragraph.getKey(ASAtom.K).size());
    }

    /** A piece over two of the element's pieces fills the gap between them, without an MCID of its own. */
    @Test
    void aPieceOverTwoPiecesOfTheElementJoinsThemEdgeToEdge() throws Exception {
        COSObject paragraph = COSDictionary.construct();
        StreamInfo left = new StreamInfo(3005, null, 0, 2);
        StreamInfo right = new StreamInfo(3005, null, 4, 6);
        StreamInfo across = new StreamInfo(3005, null, 1, 5);

        mark(paragraph, left);
        mark(paragraph, right);
        mark(paragraph, across);

        assertEquals(2, paragraph.getKey(ASAtom.K).size(), "the two MCIDs it had, no third");
        assertEquals(List.of(0, 4), List.of(left.getStartIndex(), left.getEndIndex()));
        assertEquals(List.of(4, 6), List.of(right.getStartIndex(), right.getEndIndex()));
        assertEquals(left.getMcid(), across.getMcid());
    }

    /** A stretch another element holds stays with it: the element's piece does not grow over it. */
    @Test
    void aPieceIsNotWidenedOverAnotherElementsPiece() throws Exception {
        COSObject paragraph = COSDictionary.construct();
        COSObject other = COSDictionary.construct();
        StreamInfo own = new StreamInfo(3005, null, 0, 2);
        StreamInfo theirs = new StreamInfo(3005, null, 3, 4);

        mark(paragraph, own);
        mark(other, theirs);
        mark(paragraph, new StreamInfo(3005, null, 1, 5));

        assertEquals(1, paragraph.getKey(ASAtom.K).size());
        assertEquals(List.of(0, 2), List.of(own.getStartIndex(), own.getEndIndex()));
        assertEquals(List.of(3, 4), List.of(theirs.getStartIndex(), theirs.getEndIndex()));
    }

    @Test
    void overlappingPiecesOfTwoElementsAreNotFolded() throws Exception {
        COSObject first = COSDictionary.construct();
        COSObject second = COSDictionary.construct();

        mark(first, new StreamInfo(3005, null, 1, 2));
        mark(second, new StreamInfo(3005, null, 0, 5));

        assertEquals(1, first.getKey(ASAtom.K).size());
        assertEquals(1, second.getKey(ASAtom.K).size());
    }
}
