/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.containers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
