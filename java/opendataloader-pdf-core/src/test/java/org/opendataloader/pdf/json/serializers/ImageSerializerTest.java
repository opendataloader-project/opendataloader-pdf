/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageSerializerTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private String imagesDirectory;

    @BeforeEach
    void setUp() throws IOException {
        StaticLayoutContainers.clearContainers();

        imagesDirectory = tempDir.toString();
        StaticLayoutContainers.setImagesDirectory(imagesDirectory);

        // Create a test image file
        createTestImageFile(1, "png");

        // Configure ObjectMapper with ImageSerializer
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ImageChunk.class, new ImageSerializer(ImageChunk.class));
        objectMapper.registerModule(module);
    }

    @AfterEach
    void tearDown() {
        StaticLayoutContainers.clearContainers();
    }

    private void createTestImageFile(int index, String format) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 10, 10);
        g2d.dispose();

        String fileName = String.format("%s%simageFile%d.%s", imagesDirectory, File.separator, index, format);
        File outputFile = new File(fileName);
        ImageIO.write(image, format, outputFile);
    }

    private ImageChunk createImageChunk(int index) {
        BoundingBox bbox = new BoundingBox(0, 0, 0, 100, 100);
        ImageChunk imageChunk = new ImageChunk(bbox);
        imageChunk.setIndex(index);
        return imageChunk;
    }

    @Test
    void testSerializeWithEmbedImagesTrueOutputsDataField() throws JsonProcessingException {
        StaticLayoutContainers.setEmbedImages(true);
        StaticLayoutContainers.setImageFormat("png");

        ImageChunk imageChunk = createImageChunk(1);
        String json = objectMapper.writeValueAsString(imageChunk);

        assertTrue(json.contains("\"data\":\"data:image/png;base64,"));
        assertTrue(json.contains("\"format\":\"png\""));
        assertFalse(json.contains("\"source\":"));
    }

    @Test
    void testSerializeWithEmbedImagesFalseOutputsSourceField() throws JsonProcessingException {
        StaticLayoutContainers.setEmbedImages(false);
        StaticLayoutContainers.setImageFormat("png");

        ImageChunk imageChunk = createImageChunk(1);
        String json = objectMapper.writeValueAsString(imageChunk);

        assertTrue(json.contains("\"source\":"));
        assertFalse(json.contains("\"data\":"));
        assertFalse(json.contains("\"format\":"));
    }

    @Test
    void testSerializeWithJpegFormat() throws IOException {
        createTestImageFile(2, "jpeg");
        StaticLayoutContainers.setEmbedImages(true);
        StaticLayoutContainers.setImageFormat("jpeg");

        ImageChunk imageChunk = createImageChunk(2);
        String json = objectMapper.writeValueAsString(imageChunk);

        assertTrue(json.contains("\"data\":\"data:image/jpeg;base64,"));
        assertTrue(json.contains("\"format\":\"jpeg\""));
    }

    @Test
    void testSerializeWithNonExistentImageNoSourceOrData() throws JsonProcessingException {
        StaticLayoutContainers.setEmbedImages(true);

        ImageChunk imageChunk = createImageChunk(999); // Non-existent image
        String json = objectMapper.writeValueAsString(imageChunk);

        assertFalse(json.contains("\"source\":"));
        assertFalse(json.contains("\"data\":"));
    }

    @Test
    void testSerializeContainsTypeField() throws JsonProcessingException {
        ImageChunk imageChunk = createImageChunk(1);
        String json = objectMapper.writeValueAsString(imageChunk);

        assertTrue(json.contains("\"type\":\"image\""));
    }
}
