package com.duallab.layout.utils.levels;

import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.Objects;

public class LevelInfo {
    private final double left;
    private final double right;

    public LevelInfo(double left, double right) {
        this.left = left;
        this.right = right;
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
        if (levelInfo1.isTable() || levelInfo2.isTable()) {
            return false;
        }
        if (levelInfo1.isList() && levelInfo2.isList()) {
            ListLevelInfo listLevelInfo1 = (ListLevelInfo)levelInfo1;
            ListLevelInfo listLevelInfo2 = (ListLevelInfo)levelInfo2;
            if (Objects.equals(listLevelInfo1.getNumberingStyle(), listLevelInfo2.getNumberingStyle()) &&
                    Objects.equals(listLevelInfo1.getCommonPrefix(), listLevelInfo2.getCommonPrefix())) {
                return true;
            }
        }
        if (levelInfo1.isTextBulletParagraph() && levelInfo2.isTextBulletParagraph()) {
            TextBulletParagraphLevelInfo textBulletParagraphLevelInfo1 = (TextBulletParagraphLevelInfo)levelInfo1;
            TextBulletParagraphLevelInfo textBulletParagraphLevelInfo2 = (TextBulletParagraphLevelInfo)levelInfo2;
            if (Objects.equals(textBulletParagraphLevelInfo1.getLabel(), textBulletParagraphLevelInfo2.getLabel())) {
                return true;
            }
            if (Objects.equals(textBulletParagraphLevelInfo1.getLabelRegex(), textBulletParagraphLevelInfo2.getLabelRegex())) {
                return true;
            }
        }
        if (levelInfo1.isLineArtBulletParagraph() && levelInfo2.isLineArtBulletParagraph()) {
            LineArtBulletParagraphLevelInfo lineArtBulletParagraphLevelInfo1 = (LineArtBulletParagraphLevelInfo)levelInfo1;
            LineArtBulletParagraphLevelInfo lineArtBulletParagraphLevelInfo2 = (LineArtBulletParagraphLevelInfo)levelInfo2;
            LineArtChunk bullet1 = lineArtBulletParagraphLevelInfo1.getBullet();
            LineArtChunk bullet2 = lineArtBulletParagraphLevelInfo2.getBullet();
            if (LineArtChunk.areHaveSameSizes(bullet1, bullet2)) {
                return true;
            }
        }

        return false;
    }
    
    public boolean isTable() {
        return false;
    }

    public boolean isList() {
        return false;
    }

    public boolean isLineArtBulletParagraph() {
        return false;
    }

    public boolean isTextBulletParagraph() {
        return false;
    }
}
