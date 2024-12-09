package com.duallab.layout.processors;

import com.duallab.layout.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

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
                        levelInfo = new LevelInfo((PDFList) content);
                    }
                } else if (content instanceof SemanticTextNode) {
                    if (BulletedParagraphUtils.isBulletedParagraph((SemanticTextNode) content)) {
                        levelInfo = new LevelInfo((SemanticTextNode) content);
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

    public static class LevelInfo {
        private final double left;
        private final double right;
        private final String label;
        private final boolean isList;
        private final String commonPrefix;
        private final String numberingStyle;
        
        public LevelInfo(double left, double right, String label) {
            this.left = left;
            this.right = right;
            this.label = label;
            this.isList = false;
            commonPrefix = null;
            numberingStyle = null;
        }
        
        public LevelInfo(SemanticTextNode semanticTextNode) {
            this.left = semanticTextNode.getFirstLine().getLeftX();
            this.right = semanticTextNode.getRightX();
            this.label = BulletedParagraphUtils.getLabel(semanticTextNode);
            this.isList = false;
            commonPrefix = null;
            numberingStyle = null;
        }
        
        public LevelInfo(PDFList pdfList) {
            this.left = pdfList.getFirstListItem().getFirstLine().getLeftX();
            this.right = pdfList.getRightX();
            this.label = pdfList.getFirstListItem().getFirstLine().getValue().substring(0, 1);
            this.isList = true;
            commonPrefix = pdfList.getCommonPrefix();
            numberingStyle = pdfList.getNumberingStyle();
        }
        
        public static boolean areSameLevelsInfos(LevelInfo levelInfo1, LevelInfo levelInfo2) {
            if (!areSameLevelsInfosIgnoringBoundingBoxes(levelInfo1, levelInfo2)) {
                return false;
            }
            if (levelInfo1.right < levelInfo2.left || levelInfo2.right < levelInfo1.left) {

            } else {
                if (!NodeUtils.areCloseNumbers(levelInfo1.left, levelInfo2.left)) {
                    return false;
                }
            }
            return true;
        }

        public static boolean areSameLevelsInfosIgnoringBoundingBoxes(LevelInfo levelInfo1, LevelInfo levelInfo2) {
            if (levelInfo1.isList && levelInfo2.isList) {
                if (Objects.equals(levelInfo1.numberingStyle, levelInfo2.numberingStyle) &&
                        Objects.equals(levelInfo1.commonPrefix, levelInfo2.commonPrefix)) {
                    return true;
                }
            }
            if (Objects.equals(levelInfo1.label, levelInfo2.label)) {
                return true;
            }
            return false;
        }
    }
}
