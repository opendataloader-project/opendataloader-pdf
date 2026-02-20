/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.regression;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.pd.font.cmap.ToUnicodeInterval;

/**
 * Regression tests for veraPDF ToUnicodeInterval byte overflow bug (Issue #166).
 *
 * <p>This bug caused incorrect Korean text extraction for CID fonts with bfrange
 * entries that cross byte boundaries (e.g., 0xFF -> 0x00 carry).
 *
 * <p>Fixed in veraPDF 1.31.x. These tests ensure the fix doesn't regress.
 *
 * <p>Note: Tests directly use veraPDF internal API ({@code ToUnicodeInterval}).
 * If this class is moved in a future veraPDF release, update the import accordingly.
 *
 * @see <a href="https://github.com/opendataloader-project/opendataloader-pdf/issues/166">Issue #166</a>
 */
class ToUnicodeRegressionTest {

    /**
     * Verifies that bfrange carry works correctly for Korean CID fonts.
     *
     * <p>bfrange: {@code <1ce6> <1ce7> <B2FF>}
     * <ul>
     *   <li>CID 0x1CE6 -> U+B2FF (닿)</li>
     *   <li>CID 0x1CE7 -> U+B300 (대) — requires carry from 0xFF to 0x00 in low byte</li>
     * </ul>
     *
     * <p>Before fix: returned U+B200 (눀) due to byte overflow without carry.
     * This also caused spurious spaces in page numbers (e.g. "31" → "3 1")
     * because the corrupted glyph widths affected text chunk bounding boxes.
     */
    @Test
    public void testIssue166ToUnicodeIntervalByteCarry() {
        byte[] startingValue = new byte[] { (byte) 0xB2, (byte) 0xFF };
        ToUnicodeInterval interval = new ToUnicodeInterval(0x1CE6, 0x1CE7, startingValue);

        Assertions.assertEquals("\uB2FF", interval.toUnicode(0x1CE6),
            "First mapping should be U+B2FF");
        Assertions.assertEquals("\uB300", interval.toUnicode(0x1CE7),
            "Second mapping should be U+B300 (대), not U+B200 (눀)");
    }

    /**
     * Verifies byte carry at the U+00FF -> U+0100 boundary.
     *
     * bfrange: {@code <0001> <0002> <00FF>}
     * - CID 0x0001 -> U+00FF
     * - CID 0x0002 -> U+0100 — requires carry
     *
     * Before fix: returned U+0000 (NULL) due to byte overflow.
     */
    @Test
    public void testIssue166ToUnicodeIntervalByteCarryAtLowBoundary() {
        byte[] startingValue = new byte[] { (byte) 0x00, (byte) 0xFF };
        ToUnicodeInterval interval = new ToUnicodeInterval(0x0001, 0x0002, startingValue);

        Assertions.assertEquals("\u00FF", interval.toUnicode(0x0001),
            "First mapping should be U+00FF");
        Assertions.assertEquals("\u0100", interval.toUnicode(0x0002),
            "Second mapping should be U+0100, not U+0000");
    }
}
