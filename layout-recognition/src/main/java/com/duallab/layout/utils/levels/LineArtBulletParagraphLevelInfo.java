package com.duallab.layout.utils.levels;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;

public class LineArtBulletParagraphLevelInfo extends LevelInfo {
    private final LineArtChunk bullet;
    
    public LineArtBulletParagraphLevelInfo(SemanticTextNode textNode) {
        super(0, 0);
        this.bullet = textNode.getFirstLine().getConnectedLineArtLabel();
    }

    @Override
    public boolean isLineArtBulletParagraph() {
        return true;
    }

    public LineArtChunk getBullet() {
        return bullet;
    }
}
