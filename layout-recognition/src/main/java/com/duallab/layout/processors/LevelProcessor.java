/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import com.duallab.layout.utils.BulletedParagraphUtils;
import com.duallab.layout.utils.levels.*;
import com.duallab.wcag.algorithms.entities.IObject;
import com.duallab.wcag.algorithms.entities.SemanticHeading;
import com.duallab.wcag.algorithms.entities.SemanticTextNode;
import com.duallab.wcag.algorithms.entities.lists.ListItem;
import com.duallab.wcag.algorithms.entities.lists.PDFList;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LevelProcessor {

    private static final Logger LOGGER = Logger.getLogger(LevelProcessor.class.getCanonicalName());
    
    private static boolean isDocTitleSet = false;

    public static void detectLevels(List<List<IObject>> contents) {
        setLevels(contents, new Stack<>());
    }
    
    private static void setLevels(List<List<IObject>> contents, Stack<LevelInfo> levelInfos) {
        int levelInfosSize = levelInfos.size();
        for (List<IObject> pageContents : contents) {
            for (IObject content : pageContents) {
                if (content instanceof SemanticHeading) {
                    setLevelForHeading((SemanticHeading)content);
                    continue;
                }
                LevelInfo levelInfo = null;
                Integer index = null;
                if (content instanceof PDFList) {
                    PDFList previousList = ((PDFList) content).getPreviousList();
                    if (previousList != null) {
                        if (previousList.getLevel() == null) {
                            LOGGER.log(Level.WARNING, "List without detected level");
                        } else {
                            index = Integer.parseInt(previousList.getLevel()) - 1;
                        }
                    }
                    if (index == null) {
                        levelInfo = new ListLevelInfo((PDFList) content);
                    }
                } else if (content instanceof TableBorder) {
                    TableBorder table = (TableBorder)content;
                    setLevelForTable(table);
                    if (!table.isTextBlock()) {
                        levelInfo = new TableLevelInfo(table);
                    }
                } else if (content instanceof SemanticTextNode) {
                    if (BulletedParagraphUtils.isBulletedParagraph((SemanticTextNode) content)) {
                        if (BulletedParagraphUtils.isBulletedLineArtParagraph((SemanticTextNode) content)) {
                            levelInfo = new LineArtBulletParagraphLevelInfo((SemanticTextNode) content);
                        } else {
                            levelInfo = new TextBulletParagraphLevelInfo((SemanticTextNode) content);
                        }
                    }
                }
                if (levelInfo == null && index == null) {
                    continue;
                }
                if (index == null) {
                    index = getLevelInfoIndex(levelInfos, levelInfo);
                }
                if (index == null) {
                    content.setLevel(String.valueOf(levelInfos.size() + 1));
                    levelInfos.add(levelInfo);
                } else {
                    content.setLevel(String.valueOf(index + 1));
                    for (int i = Math.max(index + 1, levelInfosSize); i < levelInfos.size(); i++) {
                        levelInfos.pop();
                    }
                }
                if (content instanceof PDFList) {
                    for (ListItem listItem : ((PDFList)content).getListItems()) {
                        setLevels(Collections.singletonList(listItem.getContents()), levelInfos);
                    }
                }
            }
        }
        isDocTitleSet = false;
    }

    private static void setLevelForHeading(SemanticHeading heading) {
        if (heading.getHeadingLevel() == 1 && !isDocTitleSet) {
            heading.setLevel("Doctitle");
            isDocTitleSet = true;
        } else {
            heading.setLevel("Subtitle");
        }
    }

    private static Integer getLevelInfoIndex(Stack<LevelInfo> levelInfos, LevelInfo levelInfo) {
        for (int index = 0; index < levelInfos.size(); index++) {
            LevelInfo currentLevelInfo = levelInfos.get(index);
            if (LevelInfo.areSameLevelsInfos(currentLevelInfo, levelInfo)) {
                return index;
            }
        }
        return null;
    }

    public static void setLevelForTable(TableBorder tableBorder) {
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = tableBorder.getRow(rowNumber);
            for (int colNumber = 0; colNumber < tableBorder.getNumberOfColumns(); colNumber++) {
                TableBorderCell tableBorderCell = row.getCell(colNumber);
                if (tableBorderCell.getRowNumber() == rowNumber && tableBorderCell.getColNumber() == colNumber) {
                    setLevels(Collections.singletonList(tableBorderCell.getContents()), new Stack<>());
                }
            }
        }
    }

}
