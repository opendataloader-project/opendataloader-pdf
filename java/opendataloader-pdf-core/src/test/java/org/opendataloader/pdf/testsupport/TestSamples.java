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
package org.opendataloader.pdf.testsupport;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.regex.Pattern;

/**
 * Synthetic samples for tests.
 *
 * <p>A test builds its sample PDF while it runs and writes it, with the output, to
 * {@code target/test-samples/<name>/}. The files stay after the test, so a person can open
 * them. Git ignores {@code target/}, and {@code mvn clean} deletes it.
 */
public final class TestSamples {

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private TestSamples() {
    }

    /**
     * Returns an empty folder {@code target/test-samples/<name>/}. If the folder exists, this
     * method deletes it and its content first.
     *
     * @param name lowercase letters, digits, and hyphens, for example {@code issue-336}
     */
    public static Path emptyDirectory(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Sample name must match " + NAME + ": " + name);
        }
        Path root = targetDirectory().resolve("test-samples").normalize();
        Path directory = root.resolve(name).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IllegalArgumentException("Sample folder is outside " + root + ": " + directory);
        }
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                deleteWithoutFollowingLinks(directory);
            }
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes the folder and its content. A symbolic link or a Windows junction is deleted
     * as a link: the walk does not go into it, so nothing outside the folder is deleted.
     */
    private static void deleteWithoutFollowingLinks(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther()) {   // a junction reports isOther()
                    Files.delete(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Returns a document with the given number of pages. Each page shows the paragraphs in Helvetica.
     * The caller closes the document.
     */
    public static PDDocument textDocument(int pages, String... paragraphs) throws IOException {
        PDDocument document = new PDDocument();
        try {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int number = 1; number <= pages; number++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 700);
                    for (String paragraph : paragraphs) {
                        content.showText(paragraph);
                        content.newLineAtOffset(0, -40);
                    }
                    content.endText();
                }
            }
            return document;
        } catch (IOException | RuntimeException e) {
            document.close();
            throw e;
        }
    }

    /** Writes {@link #textDocument(int, String...)} to {@code pdf}. */
    public static void writeTextPdf(Path pdf, int pages, String... paragraphs) throws IOException {
        try (PDDocument document = textDocument(pages, paragraphs)) {
            document.save(pdf.toFile());
        }
    }

    private static Path targetDirectory() {
        CodeSource source = TestSamples.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new IllegalStateException("Cannot find the test classes folder");
        }
        try {
            Path testClasses = Paths.get(source.getLocation().toURI());
            if (!Files.isDirectory(testClasses)) {
                throw new IllegalStateException("Test classes are not in a folder: " + testClasses);
            }
            return testClasses.getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
