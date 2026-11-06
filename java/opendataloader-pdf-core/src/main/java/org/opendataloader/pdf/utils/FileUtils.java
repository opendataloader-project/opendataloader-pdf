package org.opendataloader.pdf.utils;

import java.util.Locale;

/**
 * File-name utility helpers shared across the PDF processing pipeline.
 */
public class FileUtils {

    private FileUtils() {
        // utility class — no instances
    }

    /**
     * Returns the base name of {@code fileName} by stripping the last extension.
     *
     * <p>Uses {@link String#lastIndexOf(int) lastIndexOf('.')} so it correctly handles:
     * <ul>
     *   <li>Uppercase extensions — {@code "my_paper.PDF"} → {@code "my_paper"}</li>
     *   <li>Multi-dot filenames — {@code "report.v1.2.pdf"} → {@code "report.v1.2"}</li>
     *   <li>No extension — {@code "myfile"} → {@code "myfile"}</li>
     *   <li>Hidden files / dot at index 0 — {@code ".hidden"} → {@code ".hidden"}</li>
     * </ul>
     *
     * @param fileName the plain file name (not a full path)
     * @return the file name without its last extension, or the original string if no
     *         extension is present or the only dot is at position 0
     */
    public static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * Returns the name the outputs of an input file are named after: the input
     * name without a trailing {@code .pdf} in any case, or the whole input name
     * when it does not end in {@code .pdf}.
     *
     * <p>Only {@code .pdf} is removed, because a PDF may come under any name:
     * <ul>
     *   <li>{@code "my_paper.PDF"} → {@code "my_paper"}</li>
     *   <li>{@code "report.v1.2.pdf"} → {@code "report.v1.2"}</li>
     *   <li>{@code "3f2b9c1e"} → {@code "3f2b9c1e"}</li>
     *   <li>{@code "report.json"} → {@code "report.json"}, so its JSON output is
     *       {@code report.json.json} and does not overwrite the input</li>
     *   <li>{@code "report.txt"} → {@code "report.txt"}, so its outputs differ from
     *       those of {@code report.pdf}</li>
     * </ul>
     *
     * @param fileName the plain file name (not a full path)
     * @return the file name without a trailing {@code .pdf}
     */
    public static String getOutputBaseName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
