package com.duallab.layout.utils.levels;

import com.duallab.layout.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

public class TextBulletParagraphLevelInfo extends LevelInfo {
    private final String label;

    public TextBulletParagraphLevelInfo(SemanticTextNode semanticTextNode) {
        super(semanticTextNode.getFirstLine().getLeftX(), semanticTextNode.getRightX());
        this.label = BulletedParagraphUtils.getLabel(semanticTextNode);
    }
    
    @Override
    public boolean isTextBulletParagraph() {
        return true;
    }

    public String getLabel() {
        return label;
    }
}
