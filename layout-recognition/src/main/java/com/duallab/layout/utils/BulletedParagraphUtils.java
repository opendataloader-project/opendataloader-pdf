package com.duallab.layout.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

public class BulletedParagraphUtils {
    public static final String POSSIBLE_LABELS = "*+-.=‐‑‒–—―•‣․‧※⁃⁎→↳⇒⇨⇾∙■□▢▣▤▥▦▧▨▩▪▬▭▮▯▰▱▲△▴▵▶▷▸▹►▻▼▽▾▿◀◁◂◃◄◅◆◇◈◉◊○◌◍" +
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
        char character = textLine.getValue().charAt(0);
        return POSSIBLE_LABELS.indexOf(character) != -1;
    }
}
