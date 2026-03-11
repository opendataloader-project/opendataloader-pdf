/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

import java.util.Collections;
import java.util.List;

public class ClusterTableProcessorTest {

    @Test
    public void testCollectTableBordersSkipsMalformedTableAndContinues() {
        Table malformedTable = new Table(Collections.emptyList()) {
            @Override
            public TableBorder createTableBorderFromTable() {
                throw new IndexOutOfBoundsException("Index 2 out of bounds for length 2");
            }
        };

        TableBorder expectedBorder = new TableBorder(1, 1);
        Table validTable = new Table(Collections.emptyList()) {
            @Override
            public TableBorder createTableBorderFromTable() {
                return expectedBorder;
            }
        };

        List<TableBorder> result = ClusterTableProcessor.collectTableBorders(List.of(malformedTable, validTable));

        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(expectedBorder, result.get(0));
    }
}
