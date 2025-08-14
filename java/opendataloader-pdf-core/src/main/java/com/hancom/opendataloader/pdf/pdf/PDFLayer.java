/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.pdf;

public enum PDFLayer {
    CONTENT("content"),
    TABLE_CELLS("table cells"),
    LIST_ITEMS("list items"),
    TABLE_CONTENT("table content"),
    LIST_CONTENT("list content"),
    TEXT_BLOCK_CONTENT("text blocks content"),
    HEADER_AND_FOOTER_CONTENT("header and footer content");

    private final String value;

    PDFLayer(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
