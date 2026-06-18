
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.utils.FileUtils;

/**
 * Unit tests for the base-name extraction logic introduced to replace the
 * hard-coded {@code fileName.length() - 4} assumption (see issue #405).
 *
 * <p>The helper mirrors the expression used in {@link DocumentProcessor},
 * {@link AutoTaggingProcessor}, and {@link org.opendataloader.pdf.pdf.PDFWriter}:
 * <pre>
 *   int dotIndex = fileName.lastIndexOf('.');
 *   String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
 * </pre>
 *
 * <p>Tested cases:
 * <ul>
 *   <li>Standard lowercase {@code .pdf} extension</li>
 *   <li>Uppercase {@code .PDF} extension (was broken by {@code length() - 4})</li>
 *   <li>Multi-dot filenames, e.g. {@code report.v1.2.pdf}</li>
 *   <li>Filenames without any extension (edge case — keep full name)</li>
 *   <li>Extension-only names, e.g. {@code .hidden} (dot at index 0 → keep full name)</li>
 * </ul>
 */
public class DocumentProcessorBaseNameTest {


    @Test
    public void testStandardLowercasePdfExtension() {
        Assertions.assertEquals("my_paper", FileUtils.getBaseName("my_paper.pdf"));
    }

    @Test
    public void testUppercasePdfExtension() {
        // length() - 4 would give "my_p" for "my_paper.PDF" — lastIndexOf fixes this
        Assertions.assertEquals("my_paper", FileUtils.getBaseName("my_paper.PDF"));
    }

    @Test
    public void testMixedCaseExtension() {
        Assertions.assertEquals("my_paper", FileUtils.getBaseName("my_paper.Pdf"));
    }

    @Test
    public void testMultiDotFilename() {
        // e.g. report.v1.2.pdf — only the final extension should be stripped
        Assertions.assertEquals("report.v1.2", FileUtils.getBaseName("report.v1.2.pdf"));
    }

    @Test
    public void testMultiDotFilenameUppercase() {
        Assertions.assertEquals("report.v1.2", FileUtils.getBaseName("report.v1.2.PDF"));
    }

    @Test
    public void testFilenameWithSpaces() {
        // Spaces in the stem are kept verbatim; only the extension is removed
        Assertions.assertEquals("my paper", FileUtils.getBaseName("my paper.pdf"));
    }

    @Test
    public void testFilenameWithSpacesUppercase() {
        Assertions.assertEquals("my paper", FileUtils.getBaseName("my paper.PDF"));
    }

    @Test
    public void testNoExtension() {
        // No dot → keep the full filename unchanged
        Assertions.assertEquals("myfile", FileUtils.getBaseName("myfile"));
    }

    @Test
    public void testDotOnlyAtStart() {
        // Dot at index 0 means dotIndex == 0 (not > 0) → keep the full name
        Assertions.assertEquals(".hidden", FileUtils.getBaseName(".hidden"));
    }

    @Test
    public void testSingleCharBaseName() {
        Assertions.assertEquals("a", FileUtils.getBaseName("a.pdf"));
    }

    @Test
    public void testNonPdfExtension() {
        // The logic is extension-agnostic — strips whatever is after the last dot
        Assertions.assertEquals("archive", FileUtils.getBaseName("archive.tar"));
    }
}
