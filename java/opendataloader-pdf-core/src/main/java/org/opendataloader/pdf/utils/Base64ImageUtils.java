/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for converting images to Base64 data URIs.
 */
public final class Base64ImageUtils {
    private static final Logger LOGGER = Logger.getLogger(Base64ImageUtils.class.getCanonicalName());

    /**
     * Maximum image file size for Base64 embedding (10MB).
     * Larger images will be skipped to prevent memory exhaustion.
     */
    public static final long MAX_EMBEDDED_IMAGE_SIZE = 10L * 1024 * 1024;

    private Base64ImageUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Converts an image file to a Base64 data URI string.
     * Images larger than {@link #MAX_EMBEDDED_IMAGE_SIZE} will be skipped.
     *
     * @param imageFile The image file to convert
     * @param format The image format (png, jpeg)
     * @return The Base64 data URI string, or null if conversion fails or image is too large
     */
    public static String toDataUri(File imageFile, String format) {
        try {
            long fileSize = imageFile.length();
            if (fileSize > MAX_EMBEDDED_IMAGE_SIZE) {
                LOGGER.log(Level.WARNING, "Image too large to embed ({0} bytes, max {1} bytes): {2}",
                    new Object[]{fileSize, MAX_EMBEDDED_IMAGE_SIZE, imageFile.getName()});
                return null;
            }
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileContent);
            String mimeType = getMimeType(format);
            return String.format("data:%s;base64,%s", mimeType, base64);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to convert image to Base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the MIME type for the given image format.
     *
     * @param format The image format (png, jpeg)
     * @return The corresponding MIME type
     */
    public static String getMimeType(String format) {
        if (format == null) {
            return "image/png";
        }
        switch (format.toLowerCase()) {
            case "jpeg":
            case "jpg":
                return "image/jpeg";
            case "png":
            default:
                return "image/png";
        }
    }
}
