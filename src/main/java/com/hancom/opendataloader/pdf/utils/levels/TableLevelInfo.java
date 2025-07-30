/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.utils.levels;

import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

public class TableLevelInfo extends LevelInfo {
    public TableLevelInfo(TableBorder table) {
        super(0, 0);
    }

    @Override
    public boolean isTable() {
        return true;
    }
}
