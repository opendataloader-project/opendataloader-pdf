package org.opendataloader.pdf.utils;

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
}
