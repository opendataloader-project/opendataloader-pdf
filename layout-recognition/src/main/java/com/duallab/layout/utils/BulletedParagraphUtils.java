package com.duallab.layout.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

public class BulletedParagraphUtils {
    private static final String POSSIBLE_LABELS = "*+-.=‐‑‒–—―•‣․‧※⁃⁎→↳⇒⇨⇾∙■□▢▣▤▥▦▧▨▩▪▬▭▮▯▰▱▲△▴▵▶▷▸▹►▻▼▽▾▿◀◁◂◃◄◅◆◇◈◉◊○◌◍" +
            "◎●◐◑◒◓◔◕◖◗◘◙◢◣◤◥◦◧◨◩◪◫◬◭◮◯◰◱◲◳◴◵◶◷◸◹◺◻◼◽◾◿★☆☐☑☒☓☛☞♠♡♢♣♤♥♦♧⚪⚫⚬✓✔✕✖✗✘✙✚✛✜✝✞✟✦✧✨❍❏❐❑" +
            "❒❖➔➙➛➜➝➞➟➠➡➢➣➤➥➦➧➨➩➪➭➮➯➱⬛⬜⬝⬞⬟⬠⬡⬢⬣⬤⬥⬦⬧⬨⬩⬪⬫⬬⬭⬮⬯⭐⭑⭒⭓⭔⭕⭖⭗⭘⭙⯀⯁⯂⯃⯄⯅⯆⯇⯈⯌⯍⯎⯏⯐〇" +
            "󰁾󰋪󰋫󰋬󰋭󰋮󰋯󰋰󰋱󰋲󰋳󰋴󰋵󰋶󰋷󰋸󰋹󰋺󰋻󰋼";

    public static String getLabel(SemanticTextNode semanticTextNode) {
                return semanticTextNode.getValue().substring(0, 1);
            }

    public static boolean isBulletedParagraph(SemanticTextNode textNode) {
                return isBulletedLine(textNode.getFirstLine());
            }

    public static boolean isBulletedLine(TextLine textLine) {
        return isLabeledLine(textLine);
    }

    public static boolean isLabeledLine(TextLine textLine) {
        String value = textLine.getValue();
        char character = value.charAt(0);
        return POSSIBLE_LABELS.indexOf(character) != -1;
    }
}
