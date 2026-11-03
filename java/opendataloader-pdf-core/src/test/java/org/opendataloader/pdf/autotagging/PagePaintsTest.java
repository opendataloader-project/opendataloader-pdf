package org.opendataloader.pdf.autotagging;

import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagePaintsTest {

    /**
     * Bounding a page reads its content stream, which can be malformed. A page
     * that cannot be bounded hands out nothing: its text stays as OCR read it,
     * and the other pages and the rest of the run go on.
     */
    @Test
    void aPageThatCannotBeBoundedHandsOutNothing() {
        PagePaints paints = new PagePaints(0, () -> {
            throw new IllegalStateException("broken content stream");
        });

        assertEquals(List.of(), paints.takeInside(new BoundingBox(0, 0, 0, 100, 100), 5));
    }
}
