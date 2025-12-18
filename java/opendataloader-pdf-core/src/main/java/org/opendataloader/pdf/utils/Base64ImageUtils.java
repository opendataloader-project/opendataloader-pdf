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

    private Base64ImageUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Converts an image file to a Base64 data URI string.
     *
     * @param imageFile The image file to convert
     * @param format The image format (png, jpeg)
     * @return The Base64 data URI string, or null if conversion fails
     */
    public static String toDataUri(File imageFile, String format) {
        try {
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
            case "webp":
                return "image/webp";
            case "png":
            default:
                return "image/png";
        }
    }
}
