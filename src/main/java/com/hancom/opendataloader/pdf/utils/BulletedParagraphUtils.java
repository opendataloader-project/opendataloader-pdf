/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

import java.util.HashSet;
import java.util.Set;

public class BulletedParagraphUtils {
    private static final String POSSIBLE_LABELS = "∘*+-.=‐‑‒–—―•‣․‧※⁃⁎→↳⇒⇨⇾∙■□▢▣▤▥▦▧▨▩▪▬▭▮▯▰▱▲△▴▵▶▷▸▹►▻▼▽▾▿◀◁◂◃◄◅◆◇◈◉◊○◌◍" +
            "◎●◐◑◒◓◔◕◖◗◘◙◢◣◤◥◦◧◨◩◪◫◬◭◮◯◰◱◲◳◴◵◶◷◸◹◺◻◼◽◾◿★☆☐☑☒☓☛☞♠♡♢♣♤♥♦♧⚪⚫⚬✓✔✕✖✗✘✙✚✛✜✝✞✟✦✧✨❍❏❐❑" +
            "❒❖➔➙➛➜➝➞➟➠➡➢➣➤➥➦➧➨➩➪➭➮➯➱⬛⬜⬝⬞⬟⬠⬡⬢⬣⬤⬥⬦⬧⬨⬩⬪⬫⬬⬭⬮⬯⭐⭑⭒⭓⭔⭕⭖⭗⭘⭙⯀⯁⯂⯃⯄⯅⯆⯇⯈⯌⯍⯎⯏⯐〇" +
            "󰁾󰋪󰋫󰋬󰋭󰋮󰋯󰋰󰋱󰋲󰋳󰋴󰋵󰋶󰋷󰋸󰋹󰋺󰋻󰋼";
    private static final Set<String> BULLET_REGEXES = new HashSet<>();
    private static final Set<String> ARABIC_NUMBER_REGEXES = new HashSet<>();
    private static final String KOREAN_NUMBERS_REGEX = "[가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후그느드르므브스으즈츠크트프흐기니디리미비시이지치키티피히]";
    public static final String KOREAN_CHAPTER_REGEX = "^(제\\d+[장조절]).*";

    public static String getLabel(SemanticTextNode semanticTextNode) {
        return semanticTextNode.getValue().substring(0, 1);
    }

    public static boolean isBulletedParagraph(SemanticTextNode textNode) {
        return isBulletedLine(textNode.getFirstLine());
    }

    public static boolean isBulletedLine(TextLine textLine) {
        if (isLabeledLine(textLine)) {
            return true;
        }
        return false;
    }

    public static boolean isLabeledLine(TextLine textLine) {
        String value = textLine.getValue();
        char character = value.charAt(0);
        if (POSSIBLE_LABELS.indexOf(character) != -1) {
            return true;
        }
        if (textLine.getConnectedLineArtLabel() != null) {
            return true;
        }
        for (String regex : BULLET_REGEXES) {
            if (value.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBulletedLineArtParagraph(SemanticTextNode textNode) {
        return textNode.getFirstLine().getConnectedLineArtLabel() != null;
    }

    public static String getLabelRegex(SemanticTextNode textNode) {
        String value = textNode.getFirstLine().getValue();
        for (String regex : BULLET_REGEXES) {
            if (value.matches(regex)) {
                return regex;
            }
        }
        return null;
    }

    static {
        ARABIC_NUMBER_REGEXES.add("^\\d+[ \\.\\]\\)>].*");
        BULLET_REGEXES.add("^\\(\\d+\\).*");
        ARABIC_NUMBER_REGEXES.add("^<\\d+>.*");
        ARABIC_NUMBER_REGEXES.add("^\\[\\d+\\].*");
        ARABIC_NUMBER_REGEXES.add("^{\\d+}.*");
        ARABIC_NUMBER_REGEXES.add("^【\\d+】.*");
        BULLET_REGEXES.add("^\\d+[\\.\\)]\\s+.*");
        BULLET_REGEXES.add("^[ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ][\\.\\)\\]>].*");
        BULLET_REGEXES.add("^" + KOREAN_NUMBERS_REGEX + "\\..+");
        BULLET_REGEXES.add("^" + KOREAN_NUMBERS_REGEX + "[)\\]>].*");
        BULLET_REGEXES.add("^" + KOREAN_NUMBERS_REGEX + "(-\\d+).*");
        BULLET_REGEXES.add("^\\(" + KOREAN_NUMBERS_REGEX + "\\).*");
        BULLET_REGEXES.add("^<" + KOREAN_NUMBERS_REGEX + ">.*");
        BULLET_REGEXES.add("^\\[" + KOREAN_NUMBERS_REGEX + "\\].*");
        BULLET_REGEXES.add("^[{]" + KOREAN_NUMBERS_REGEX + "[}].*");
        BULLET_REGEXES.add(KOREAN_CHAPTER_REGEX);
        BULLET_REGEXES.add("^법\\.(제\\d+조).*");
        BULLET_REGEXES.add("^[\u0049]\\..*");//"^[Ⅰ-Ⅻ]"
        BULLET_REGEXES.add("^[\u2160-\u216B].*");//"^[Ⅰ-Ⅻ]"
        BULLET_REGEXES.add("^[\u2170-\u217B].*");//"^[ⅰ-ⅻ]"
        BULLET_REGEXES.add("^[\u2460-\u2473].*");//"^[①-⑳]"
        BULLET_REGEXES.add("^[\u2474-\u2487].*");//"^[⑴-⒇]"
        BULLET_REGEXES.add("^[\u2488-\u249B].*");//"^[⒈-⒛]"
        BULLET_REGEXES.add("^[\u249C-\u24B5].*");//"^[⒜-⒵]"
        BULLET_REGEXES.add("^[\u24B6-\u24CF].*");//"^[Ⓐ-Ⓩ]"
        BULLET_REGEXES.add("^[\u24D0-\u24E9].*");//"^[ⓐ-ⓩ]"
        BULLET_REGEXES.add("^[\u24F5-\u24FE].*");//"^[⓵-⓾]"
        BULLET_REGEXES.add("^[\u2776-\u277F].*");//"^[❶-❿]"
        BULLET_REGEXES.add("^[\u2780-\u2789].*");//"^[➀-➉]"
        BULLET_REGEXES.add("^[\u278A-\u2793].*");//"^[➊-➓]"
        BULLET_REGEXES.add("^[\u326E-\u327B].*");//"^[㉮-㉻]"
        BULLET_REGEXES.add("^[\uF081-\uF08A].*");//"^[-]"
        BULLET_REGEXES.add("^[\uF08C-\uF095].*");//"^[-]"
    }
}
