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
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link FileUtils#getOutputBaseName(String)} removes a trailing {@code .pdf} and
 * nothing else, so an output is never named like its input.
 */
class FileUtilsOutputBaseNameTest {

    @Test
    void removesPdfInAnyCase() {
        assertEquals("my_paper", FileUtils.getOutputBaseName("my_paper.pdf"));
        assertEquals("my_paper", FileUtils.getOutputBaseName("my_paper.PDF"));
        assertEquals("my_paper", FileUtils.getOutputBaseName("my_paper.Pdf"));
    }

    @Test
    void removesOnlyTheLastPdf() {
        assertEquals("report.v1.2", FileUtils.getOutputBaseName("report.v1.2.pdf"));
        assertEquals("a.pdf", FileUtils.getOutputBaseName("a.pdf.pdf"));
    }

    @Test
    void keepsANameWithoutAnExtension() {
        assertEquals("3f2b9c1e", FileUtils.getOutputBaseName("3f2b9c1e"));
    }

    @Test
    void keepsAnotherExtension() {
        assertEquals("report.json", FileUtils.getOutputBaseName("report.json"));
        assertEquals("scan.txt", FileUtils.getOutputBaseName("scan.txt"));
    }

    /** For each format, the output of an input named like that format gets another name. */
    @Test
    void outputNeverTakesTheNameOfItsInput() {
        for (String input : new String[] {"report.json", "notes.md", "page.html", "scan.txt"}) {
            String extension = input.substring(input.lastIndexOf('.'));
            assertNotEquals(input, FileUtils.getOutputBaseName(input) + extension, input);
        }
    }

    @Test
    void inputsWithTheSameStemGetDifferentOutputs() {
        assertNotEquals(FileUtils.getOutputBaseName("report.pdf"), FileUtils.getOutputBaseName("report.txt"));
    }
}
