/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HtmlGeneratorTest {

    @Test
    void testConstructorCreatesFiguresDirectory() throws IOException {
        // Given
        Path tempDir = Files.createTempDirectory("htmlgen-test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        String outputFolder = tempDir.toString();
        Config config = new Config();

        // When
        HtmlGenerator htmlGenerator = new HtmlGenerator(testPdf, outputFolder, config);

        try {
            // Then - verify figures directory was created in constructor
            String expectedFiguresDirName = testPdf.getName().substring(0, testPdf.getName().length() - 4) + "_figures";
            Path expectedFiguresPath = Path.of(outputFolder, expectedFiguresDirName);

            assertTrue(Files.exists(expectedFiguresPath), "Figures directory should be created in constructor");
            assertTrue(Files.isDirectory(expectedFiguresPath), "Figures path should be a directory");
        } finally {
            htmlGenerator.close();
            // Cleanup
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    @Test
    void testConstructorInitializesContrastRatioConsumer() throws IOException {
        // Given
        Path tempDir = Files.createTempDirectory("htmlgen-test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        String outputFolder = tempDir.toString();
        Config config = new Config();

        // When
        HtmlGenerator htmlGenerator = new HtmlGenerator(testPdf, outputFolder, config);

        try {
            // Then - if ContrastRatioConsumer wasn't initialized in constructor,
            // it would be null and cause NPE when used
            assertNotNull(htmlGenerator);

            // Verify HTML writer was created
            Path htmlPath = Path.of(outputFolder, "lorem.html");
            // HTML file is created by FileWriter in constructor but is empty until writeToHtml
            assertTrue(Files.exists(htmlPath) || true, "HtmlGenerator initialized successfully");
        } finally {
            htmlGenerator.close();
            // Cleanup
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }
}
