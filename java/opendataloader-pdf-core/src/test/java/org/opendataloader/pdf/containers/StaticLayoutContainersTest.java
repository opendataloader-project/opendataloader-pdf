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
package org.opendataloader.pdf.containers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StaticLayoutContainersTest {

    @BeforeEach
    void setUp() {
        StaticLayoutContainers.clearContainers();
    }

    @Test
    void testClearContainers_resetsEmbedImages() {
        StaticLayoutContainers.setEmbedImages(true);
        assertTrue(StaticLayoutContainers.isEmbedImages());

        StaticLayoutContainers.clearContainers();

        assertFalse(StaticLayoutContainers.isEmbedImages());
    }

    @Test
    void testClearContainers_resetsImageFormat() {
        StaticLayoutContainers.setImageFormat("jpeg");
        assertEquals("jpeg", StaticLayoutContainers.getImageFormat());

        StaticLayoutContainers.clearContainers();

        assertEquals("png", StaticLayoutContainers.getImageFormat());
    }

    @Test
    void testSetAndGetEmbedImages() {
        assertFalse(StaticLayoutContainers.isEmbedImages());

        StaticLayoutContainers.setEmbedImages(true);
        assertTrue(StaticLayoutContainers.isEmbedImages());

        StaticLayoutContainers.setEmbedImages(false);
        assertFalse(StaticLayoutContainers.isEmbedImages());
    }

    @Test
    void testSetAndGetImageFormat() {
        assertEquals("png", StaticLayoutContainers.getImageFormat());

        StaticLayoutContainers.setImageFormat("jpeg");
        assertEquals("jpeg", StaticLayoutContainers.getImageFormat());

        StaticLayoutContainers.setImageFormat("png");
        assertEquals("png", StaticLayoutContainers.getImageFormat());
    }

    @Test
    void testGetImageFormat_withNullValue_returnsDefaultPng() {
        StaticLayoutContainers.setImageFormat(null);

        assertEquals("png", StaticLayoutContainers.getImageFormat());
    }

    @Test
    void testIsEmbedImages_withNullValue_returnsFalse() {
        // After clearContainers, embedImages is set to false
        // This test verifies the Boolean.TRUE.equals() null-safe check
        assertFalse(StaticLayoutContainers.isEmbedImages());
    }

    @Test
    void testSetImagesDirectory() {
        assertEquals("", StaticLayoutContainers.getImagesDirectory());

        StaticLayoutContainers.setImagesDirectory("/path/to/images");
        assertEquals("/path/to/images", StaticLayoutContainers.getImagesDirectory());
    }

    @Test
    void testIncrementImageIndex() {
        StaticLayoutContainers.resetImageIndex();

        assertEquals(1, StaticLayoutContainers.incrementImageIndex());
        assertEquals(2, StaticLayoutContainers.incrementImageIndex());
        assertEquals(3, StaticLayoutContainers.incrementImageIndex());
    }

    @Test
    void testResetImageIndex() {
        StaticLayoutContainers.incrementImageIndex();
        StaticLayoutContainers.incrementImageIndex();

        StaticLayoutContainers.resetImageIndex();

        assertEquals(1, StaticLayoutContainers.incrementImageIndex());
    }

    @Test
    void testCurrentContentId() {
        StaticLayoutContainers.setCurrentContentId(100);
        assertEquals(100, StaticLayoutContainers.getCurrentContentId());

        long id = StaticLayoutContainers.incrementContentId();
        assertEquals(100, id);
        assertEquals(101, StaticLayoutContainers.getCurrentContentId());
    }

    /**
     * Regression guard for issue #458 (OOM).
     *
     * Before the hotfix, a failed {@link ContrastRatioConsumer} construction
     * was logged at {@link Level#WARNING} with only the message string —
     * the throwable was dropped, and image extraction plus hidden-text
     * filtering were then silently disabled for the rest of the document.
     * That made OOM and PDFBox failures very hard to diagnose downstream.
     *
     * The hotfix raises the level to {@link Level#SEVERE} and passes the
     * causing {@link Throwable} so the full stack trace is preserved.
     * This test attaches a capturing {@link Handler} to the StaticLayoutContainers
     * logger, forces an initialization failure via a non-existent PDF path,
     * and asserts the published {@link LogRecord} reflects both contract
     * changes plus the {@code isContrastRatioConsumerFailedToCreate} latch.
     */
    @Test
    void getContrastRatioConsumer_logsSevereWithThrowableOnInitFailure() {
        Logger logger = Logger.getLogger(StaticLayoutContainers.class.getCanonicalName());
        List<LogRecord> captured = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                captured.add(record);
            }
            @Override public void flush() { }
            @Override public void close() throws SecurityException { }
        };
        Level previousLevel = logger.getLevel();
        boolean previousUseParent = logger.getUseParentHandlers();
        logger.addHandler(capture);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            String bogusPath = "/nonexistent/issue458-oom-trigger.pdf";

            ContrastRatioConsumer result =
                StaticLayoutContainers.getContrastRatioConsumer(bogusPath, "", false, null);

            assertNull(result,
                "getContrastRatioConsumer must return null after init failure (issue #458)");

            LogRecord severe = captured.stream()
                .filter(r -> r.getLevel() == Level.SEVERE)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "Expected a SEVERE log record on ContrastRatioConsumer init failure (issue #458). " +
                    "Captured levels: " + captured.stream().map(LogRecord::getLevel).collect(Collectors.toList())));
            assertNotNull(severe.getThrown(),
                "SEVERE log record must include the causing Throwable so the stack trace is preserved (issue #458)");
            assertTrue(severe.getMessage().contains(bogusPath),
                "SEVERE log message must mention the source PDF path for diagnosability: was '" + severe.getMessage() + "'");

            // The latch is part of the documented silent-skip behaviour the hotfix
            // makes visible — it must be set so callers know subsequent gets will
            // short-circuit to null without re-attempting construction.
            ContrastRatioConsumer second =
                StaticLayoutContainers.getContrastRatioConsumer(bogusPath, "", false, null);
            assertNull(second,
                "After init failure, subsequent calls must keep returning null without retrying (issue #458)");
            long severeCount = captured.stream().filter(r -> r.getLevel() == Level.SEVERE).count();
            assertEquals(1, severeCount,
                "SEVERE must be logged exactly once — the latch prevents repeated init attempts");
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParent);
            StaticLayoutContainers.clearContainers();
        }
    }
}
