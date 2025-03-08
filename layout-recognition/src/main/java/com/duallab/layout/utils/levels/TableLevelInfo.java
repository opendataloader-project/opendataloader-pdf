/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.utils.levels;

import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorder;

public class TableLevelInfo extends LevelInfo {
    public TableLevelInfo(TableBorder table) {
        super(0, 0);
    }

    @Override
    public boolean isTable() {
        return true;
    }
}
