package com.duallab.layout.utils.levels;

import com.duallab.layout.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.Objects;

public class LevelInfo {
    
    protected static final double X_GAP_MULTIPLIER = 0.3;
    
    private final double left;
    private final double right;

    public LevelInfo(double left, double right) {
        this.left = left;
        this.right = right;
    }

    public static boolean areSameLevelsInfos(LevelInfo levelInfo1, LevelInfo levelInfo2) {
        if (levelInfo1.isTable() || levelInfo2.isTable()) {
            return false;
        }
        boolean checkBoundingBox = false;
        if (levelInfo1.isList() && levelInfo2.isList()) {
            ListLevelInfo listLevelInfo1 = (ListLevelInfo)levelInfo1;
            ListLevelInfo listLevelInfo2 = (ListLevelInfo)levelInfo2;
            if (Objects.equals(listLevelInfo1.getNumberingStyle(), listLevelInfo2.getNumberingStyle()) &&
                    Objects.equals(listLevelInfo1.getCommonPrefix(), listLevelInfo2.getCommonPrefix())) {
                checkBoundingBox = true;
            }
        } else if (levelInfo1.isTextBulletParagraph() && levelInfo2.isTextBulletParagraph()) {
            TextBulletParagraphLevelInfo textBulletParagraphLevelInfo1 = (TextBulletParagraphLevelInfo)levelInfo1;
            TextBulletParagraphLevelInfo textBulletParagraphLevelInfo2 = (TextBulletParagraphLevelInfo)levelInfo2;
            if (Objects.equals(textBulletParagraphLevelInfo1.getLabel(), textBulletParagraphLevelInfo2.getLabel())) {
                checkBoundingBox = true;
            }
            if (Objects.equals(textBulletParagraphLevelInfo1.getLabelRegex(), textBulletParagraphLevelInfo2.getLabelRegex())) {
                if (Objects.equals(textBulletParagraphLevelInfo1.getLabelRegex(), BulletedParagraphUtils.KOREAN_CHAPTER_REGEX)) {
                    return true;
                }
                checkBoundingBox = true;
            }
        } else if (levelInfo1.isLineArtBulletParagraph() && levelInfo2.isLineArtBulletParagraph()) {
            LineArtBulletParagraphLevelInfo lineArtBulletParagraphLevelInfo1 = (LineArtBulletParagraphLevelInfo)levelInfo1;
            LineArtBulletParagraphLevelInfo lineArtBulletParagraphLevelInfo2 = (LineArtBulletParagraphLevelInfo)levelInfo2;
            LineArtChunk bullet1 = lineArtBulletParagraphLevelInfo1.getBullet();
            LineArtChunk bullet2 = lineArtBulletParagraphLevelInfo2.getBullet();
            if (LineArtChunk.areHaveSameSizes(bullet1, bullet2)) {
                checkBoundingBox = true;
            }
        }
        return checkBoundingBox ? checkBoundingBoxes(levelInfo1, levelInfo2) : false;
    }

    public static boolean checkBoundingBoxes(LevelInfo levelInfo1, LevelInfo levelInfo2) {
        if (levelInfo1.right < levelInfo2.left || levelInfo2.right < levelInfo1.left) {

        } else {
            if (!NodeUtils.areCloseNumbers(levelInfo1.left, levelInfo2.left, getMaxXGap(levelInfo1, levelInfo2)) && 
                    !NodeUtils.areCloseNumbers(levelInfo1.right, levelInfo2.right, getMaxXGap(levelInfo1, levelInfo2))) {
                return false;
            }
        }
        return true;
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
    
    public double getMaxXGap() {
        return 0;
    }
    
    public static double getMaxXGap(LevelInfo levelInfo1, LevelInfo levelInfo2) {
        return Math.max(levelInfo1.getMaxXGap(), levelInfo2.getMaxXGap());
    }
}
