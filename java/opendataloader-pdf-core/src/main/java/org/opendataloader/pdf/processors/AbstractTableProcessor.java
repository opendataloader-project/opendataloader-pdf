/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * Abstract base class for table detection processors.
 * Provides common functionality for detecting and processing tables in PDF documents.
 */
public abstract class AbstractTableProcessor {

    private static final double Y_DIFFERENCE_EPSILON = 0.1;
    private static final double X_DIFFERENCE_EPSILON = 3;
    private static final double TABLE_INTERSECTION_PERCENT = 0.01;

    /**
     * Processes tables across all pages that may contain tables.
     *
     * @param contents the document contents organized by page
     */
    public void processTables(List<List<IObject>> contents) {
        List<Integer> pageNumbers = getPagesWithPossibleTables(contents);
        processTables(contents, pageNumbers);
    }

    /**
     * Processes tables on specified pages.
     *
     * @param contents the document contents organized by page
     * @param pageNumbers the list of page numbers to process
     */
    public void processTables(List<List<IObject>> contents, List<Integer> pageNumbers) {
        if (!pageNumbers.isEmpty()) {
            List<List<TableBorder>> tables = getTables(contents, pageNumbers);
            addTablesToTableCollection(tables, pageNumbers);
        }
    }

    /**
     * Detects tables on the specified pages.
     *
     * @param contents the document contents organized by page
     * @param pageNumbers the list of page numbers to process
     * @return a list of detected tables for each page
     */
    protected abstract List<List<TableBorder>> getTables(List<List<IObject>> contents, List<Integer> pageNumbers);

    private static void addTablesToTableCollection(List<List<TableBorder>> detectedTables, List<Integer> pageNumbers) {
        if (detectedTables != null) {
            TableBordersCollection tableCollection = StaticContainers.getTableBordersCollection();
            for (int index = 0; index < pageNumbers.size(); index++) {
                SortedSet<TableBorder> tables = tableCollection.getTableBorders(pageNumbers.get(index));
                for (TableBorder border : detectedTables.get(index)) {
                    boolean hasIntersections = false;
                    for (TableBorder table : tables) {
                        if (table.getBoundingBox().getIntersectionPercent(border.getBoundingBox()) > TABLE_INTERSECTION_PERCENT) {
                            hasIntersections = true;
                            break;
                        }
                    }
                    if (!hasIntersections) {
                        tables.add(border);
                    }
                }
            }
        }
    }

    /**
     * Identifies pages that may contain tables based on text chunk patterns.
     *
     * @param contents the document contents organized by page
     * @return a list of page numbers that may contain tables
     */
    public static List<Integer> getPagesWithPossibleTables(List<List<IObject>> contents) {
        List<Integer> pageNumbers = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            TextChunk previousTextChunk = null;
            for (IObject content : contents.get(pageNumber)) {
                if (content instanceof TextChunk) {
                    TextChunk currentTextChunk = (TextChunk) content;
                    if (currentTextChunk.isWhiteSpaceChunk()) {
                        continue;
                    }
                    if (previousTextChunk != null && areSuspiciousTextChunks(previousTextChunk, currentTextChunk)) {
                        pageNumbers.add(pageNumber);
                        break;
                    }
                    previousTextChunk = currentTextChunk;
                }
            }
        }
        return pageNumbers;
    }

    private static boolean areSuspiciousTextChunks(TextChunk previousTextChunk, TextChunk currentTextChunk) {
        if (previousTextChunk.getTopY() < currentTextChunk.getBottomY()) {
            return true;
        }
        if (NodeUtils.areCloseNumbers(previousTextChunk.getBaseLine(), currentTextChunk.getBaseLine(),
            currentTextChunk.getHeight() * Y_DIFFERENCE_EPSILON)) {
            if (currentTextChunk.getLeftX() - previousTextChunk.getRightX() >
                currentTextChunk.getHeight() * X_DIFFERENCE_EPSILON) {
                return true;
            }
        }
        return false;
    }
}
