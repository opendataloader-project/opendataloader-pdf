/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.known;

import org.junit.jupiter.api.Test;
import org.verapdf.pd.font.cmap.ToUnicodeInterval;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for known issues in upstream dependencies.
 * These tests document bugs that exist in external libraries.
 * They use assumeTrue() to pass when the bug exists (expected behavior)
 * and will start failing (which is good!) when the upstream bug is fixed.
 */
public class KnownIssueTest {

    private static final Logger LOGGER = Logger.getLogger(KnownIssueTest.class.getName());

    /**
     * GitHub Issue #166: Incorrect Korean text extraction for CID fonts with missing ToUnicode CMap
     *
     * Root cause: veraPDF ToUnicodeInterval.toUnicode() has a byte overflow bug.
     * When incrementing Unicode values in bfrange mappings, it only increments
     * the last byte without carrying over to higher bytes.
     *
     * Example: bfrange <1ce6> <1ce7> <B2FF>
     * - CID 0x1CE6 -> U+B2FF (correct)
     * - CID 0x1CE7 -> should be U+B300 (대) but returns U+B200 (눀)
     *
     * The bug: 0xB2FF + 1 = 0xB300, but veraPDF calculates:
     * last_byte = 0xFF, (0xFF + 1) & 0xFF = 0x00, result = 0xB200
     *
     * @see <a href="https://github.com/nicktobey/opendataloader-pdf/issues/166">Issue #166</a>
     */
    @Test
    public void testIssue166VeraPdfToUnicodeIntervalByteOverflow() {
        // bfrange: <1ce6> <1ce7> <B2FF>
        // This maps CID 0x1CE6 to U+B2FF, and CID 0x1CE7 should map to U+B300
        long intervalBegin = 0x1CE6;
        long intervalEnd = 0x1CE7;
        byte[] startingValue = new byte[] { (byte) 0xB2, (byte) 0xFF }; // U+B2FF

        ToUnicodeInterval interval = new ToUnicodeInterval(intervalBegin, intervalEnd, startingValue);

        // Test first mapping (should work correctly)
        String firstMapping = interval.toUnicode(0x1CE6);
        assertEquals("\uB2FF", firstMapping, "First mapping should be U+B2FF");

        // Test second mapping - this is where the bug manifests
        String secondMapping = interval.toUnicode(0x1CE7);
        String expected = "\uB300"; // 대 (correct)
        String buggyResult = "\uB200"; // 눀 (incorrect due to byte overflow)

        if (secondMapping.equals(buggyResult)) {
            // Bug still exists in veraPDF - log warning but pass the test
            LOGGER.warning("\n"
                    + "╔══════════════════════════════════════════════════════════════════════╗\n"
                    + "║  KNOWN ISSUE #166: veraPDF ToUnicodeInterval byte overflow bug       ║\n"
                    + "╠══════════════════════════════════════════════════════════════════════╣\n"
                    + "║  Expected: U+B300 (대)                                               ║\n"
                    + "║  Actual:   U+B200 (눀)                                               ║\n"
                    + "║                                                                      ║\n"
                    + "║  This is a known bug in veraPDF's ToUnicodeInterval.toUnicode()     ║\n"
                    + "║  The last byte overflows without carrying to higher bytes.          ║\n"
                    + "║                                                                      ║\n"
                    + "║  Upstream fix required in: org.verapdf.pd.font.cmap.ToUnicodeInterval║\n"
                    + "╚══════════════════════════════════════════════════════════════════════╝\n");

            // Use assumeTrue to mark this as a known issue - test passes but is "skipped"
            // When veraPDF fixes this bug, the assumption will fail and test will run properly
            assumeTrue(false, "Known issue #166: veraPDF ToUnicodeInterval byte overflow bug exists. "
                    + "Expected U+B300 (대) but got U+B200 (눀). "
                    + "This test will start running when veraPDF fixes the bug.");
        }

        // If we reach here, the bug is fixed! Verify correct behavior.
        assertEquals(expected, secondMapping,
                "CID 0x1CE7 should map to U+B300 (대), not U+B200 (눀)");

        LOGGER.info("Issue #166 appears to be FIXED in veraPDF! The byte overflow bug no longer exists.");
    }

    /**
     * Additional test case for Issue #166 with different byte boundary.
     * Tests carry from 0xFF to 0x00 in higher byte ranges.
     */
    @Test
    public void testIssue166VeraPdfToUnicodeIntervalByteOverflowAdditionalCase() {
        // Another example: bfrange that would require carry
        // <0001> <0002> <00FF> should map:
        // - CID 0x0001 -> U+00FF
        // - CID 0x0002 -> U+0100 (correct) but veraPDF returns U+0000 (buggy)
        long intervalBegin = 0x0001;
        long intervalEnd = 0x0002;
        byte[] startingValue = new byte[] { (byte) 0x00, (byte) 0xFF }; // U+00FF

        ToUnicodeInterval interval = new ToUnicodeInterval(intervalBegin, intervalEnd, startingValue);

        String secondMapping = interval.toUnicode(0x0002);
        String expected = "\u0100"; // Ā (Latin Capital Letter A with Macron)
        String buggyResult = "\u0000"; // NULL (incorrect)

        if (secondMapping.equals(buggyResult)) {
            LOGGER.warning("Known issue #166 (additional case): "
                    + "Expected U+0100 but got U+0000 due to byte overflow.");
            assumeTrue(false, "Known issue #166 additional case: byte overflow at U+00FF boundary.");
        }

        assertEquals(expected, secondMapping,
                "CID 0x0002 should map to U+0100, not U+0000");
    }
}
