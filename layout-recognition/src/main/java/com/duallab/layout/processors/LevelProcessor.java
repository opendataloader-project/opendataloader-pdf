package com.duallab.layout.processors;

import com.duallab.layout.utils.BulletedParagraphUtils;
import com.duallab.layout.utils.levels.*;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LevelProcessor {

    private static final Logger LOGGER = Logger.getLogger(LevelProcessor.class.getCanonicalName());

    public static void detectLevels(List<List<IObject>> contents) {
        setLevels(contents, 0);
    }
    
    private static void setLevels(List<List<IObject>> contents, Integer startIndex) {
        Stack<LevelInfo> levelInfos = new Stack<>();
        for (List<IObject> pageContents : contents) {
            for (IObject content : pageContents) {
                LevelInfo levelInfo = null;
                Integer index = null;
                if (content instanceof PDFList) {
                    PDFList previousList = ((PDFList) content).getPreviousList();
                    if (previousList != null) {
                        if (previousList.getLevel() == null) {
                            LOGGER.log(Level.WARNING, "List without detected level");
                        } else {
                            index = previousList.getLevel() - startIndex - 1;
                        }
                    }
                    if (index == null) {
                        levelInfo = new ListLevelInfo((PDFList) content);
                    }
                } else if (content instanceof TableBorder && !((TableBorder)content).isTextBlock()) {
                    levelInfo = new TableLevelInfo((TableBorder) content);
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
                    content.setLevel(levelInfos.size() + 1 + startIndex);
                    levelInfos.add(levelInfo);
                } else {
                    content.setLevel(index + 1 + startIndex);
                    while (levelInfos.size() > index + 1) {
                        levelInfos.pop();
                    }
                }
                if (content instanceof PDFList) {
                    for (ListItem listItem : ((PDFList)content).getListItems()) {
                        setLevels(Collections.singletonList(listItem.getContents()), startIndex + levelInfos.size());
                    }
                }
            }
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

}
