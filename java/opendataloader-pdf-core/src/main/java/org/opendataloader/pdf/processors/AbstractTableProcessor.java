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

public abstract class AbstractTableProcessor {

    private static final double Y_DIFFERENCE_EPSILON = 0.1;
    private static final double X_DIFFERENCE_EPSILON = 3;
    private static final double TABLE_INTERSECTION_PERCENT = 0.01;

    public void processTables(List<List<IObject>> contents) {
        List<Integer> pageNumbers = getPagesWithPossibleTables(contents);
        processTables(contents, pageNumbers);
    }

    public void processTables(List<List<IObject>> contents, List<Integer> pageNumbers) {
        if (!pageNumbers.isEmpty()) {
            List<List<TableBorder>> tables = getTables(contents, pageNumbers);
            addTablesToTableCollection(tables, pageNumbers);
        }
    }

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
