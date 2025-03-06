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
