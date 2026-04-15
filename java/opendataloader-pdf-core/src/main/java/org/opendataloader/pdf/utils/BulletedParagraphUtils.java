/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for detecting and processing bulleted paragraphs and list items.
 * Provides methods to identify various bullet and label formats including symbols,
 * numbers, Korean characters, and special Unicode characters.
 */
public class BulletedParagraphUtils {
    private static final String POSSIBLE_LABELS = "∘*+-.=‐‑‒–—―•‣․‧※⁃⁎→↳⇒⇨⇾∙■□▢▣▤▥▦▧▨▩▪▬▭▮▯▰▱▲△▴▵▶▷▸▹►▻▼▽▾▿◀◁◂◃◄◅◆◇◈◉◊○◌◍" +
            "◎●◐◑◒◓◔◕◖◗◘◙◢◣◤◥◦◧◨◩◪◫◬◭◮◯◰◱◲◳◴◵◶◷◸◹◺◻◼◽◾◿★☆☐☑☒☓☛☞♠♡♢♣♤♥♦♧⚪⚫⚬✓✔✕✖✗✘✙✚✛✜✝✞✟✦✧✨❍❏❐❑" +
            "❒❖➔➙➛➜➝➞➟➠➡➢➣➤➥➦➧➨➩➪➭➮➯➱⬛⬜⬝⬞⬟⬠⬡⬢⬣⬤⬥⬦⬧⬨⬩⬪⬫⬬⬭⬮⬯⭐⭑⭒⭓⭔⭕⭖⭗⭘⭙⯀⯁⯂⯃⯄⯅⯆⯇⯈⯌⯍⯎⯏⯐〇" +
            "󰁾󰋪󰋫󰋬󰋭󰋮󰋯󰋰󰋱󰋲󰋳󰋴󰋵󰋶󰋷󰋸󰋹󰋺󰋻󰋼";
    private static final List<Pattern> BULLET_PATTERNS = new ArrayList<>();
    private static final Set<String> ARABIC_NUMBER_REGEXES = new HashSet<>();
    private static final String KOREAN_NUMBERS_REGEX = "[가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후그느드르므브스으즈츠크트프흐기니디리미비시이지치키티피히]";
    /** Regular expression for Korean chapter patterns like 제1장, 제2조, 제3절. */
    public static final String KOREAN_CHAPTER_REGEX = "^(제\\d+[장조절]).*";

    /**
     * Gets the first character label from a text node.
     *
     * @param semanticTextNode the text node to extract the label from
     * @return the first character of the text node value
     */
    public static String getLabel(SemanticTextNode semanticTextNode) {
        return semanticTextNode.getValue().substring(0, 1);
    }

    /**
     * Checks if a text node starts with a bullet or list marker.
     *
     * @param textNode the text node to check
     * @return true if the first line is bulleted, false otherwise
     */
    public static boolean isBulletedParagraph(SemanticTextNode textNode) {
        return isBulletedLine(textNode.getFirstLine());
    }

    /**
     * Checks if a text line starts with a bullet or list marker.
     *
     * @param textLine the text line to check
     * @return true if the line is bulleted, false otherwise
     */
    public static boolean isBulletedLine(TextLine textLine) {
        if (isLabeledLine(textLine)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if a text line starts with a recognized label character or pattern.
     *
     * @param textLine the text line to check
     * @return true if the line has a recognized label, false otherwise
     */
    public static boolean isLabeledLine(TextLine textLine) {
        String value = textLine.getValue();
        if (value == null || value.isEmpty()) {
            return false;
        }
        char character = value.charAt(0);
        if (POSSIBLE_LABELS.indexOf(character) != -1) {
            return true;
        }
        if (textLine.getConnectedLineArtLabel() != null) {
            return true;
        }
        for (Pattern pattern : BULLET_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a text node has a connected line art label (graphical bullet).
     *
     * @param textNode the text node to check
     * @return true if the first line has a connected line art label, false otherwise
     */
    public static boolean isBulletedLineArtParagraph(SemanticTextNode textNode) {
        return textNode.getFirstLine().getConnectedLineArtLabel() != null;
    }

    /**
     * Finds the matching regex pattern for a text node's label.
     *
     * @param textNode the text node to analyze
     * @return the matching regex pattern, or null if no pattern matches
     */
    public static String getLabelRegex(SemanticTextNode textNode) {
        String value = textNode.getFirstLine().getValue();
        for (Pattern pattern : BULLET_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                return pattern.pattern();
            }
        }
        return null;
    }

    static {
        ARABIC_NUMBER_REGEXES.add("^\\d+[ \\.\\]\\)>].*");
        BULLET_PATTERNS.add(Pattern.compile("^\\(\\d+\\).*"));
        ARABIC_NUMBER_REGEXES.add("^<\\d+>.*");
        ARABIC_NUMBER_REGEXES.add("^\\[\\d+\\].*");
        ARABIC_NUMBER_REGEXES.add("^{\\d+}.*");
        ARABIC_NUMBER_REGEXES.add("^【\\d+】.*");
        BULLET_PATTERNS.add(Pattern.compile("^\\d+[\\.\\)]\\s+.*"));
        BULLET_PATTERNS.add(Pattern.compile("^[ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ][\\.\\)\\]>].*"));
        BULLET_PATTERNS.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "\\..+"));
        BULLET_PATTERNS.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "[)\\]>].*"));
        BULLET_PATTERNS.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "(-\\d+).*"));
        BULLET_PATTERNS.add(Pattern.compile("^\\(" + KOREAN_NUMBERS_REGEX + "\\).*"));
        BULLET_PATTERNS.add(Pattern.compile("^<" + KOREAN_NUMBERS_REGEX + ">.*"));
        BULLET_PATTERNS.add(Pattern.compile("^\\[" + KOREAN_NUMBERS_REGEX + "\\].*"));
        BULLET_PATTERNS.add(Pattern.compile("^[{]" + KOREAN_NUMBERS_REGEX + "[}].*"));
        BULLET_PATTERNS.add(Pattern.compile(KOREAN_CHAPTER_REGEX));
        BULLET_PATTERNS.add(Pattern.compile("^법\\.(제\\d+조).*"));
        BULLET_PATTERNS.add(Pattern.compile("^[\u0049]\\..*"));//"^[Ⅰ-Ⅻ]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2160-\u216B].*"));//"^[Ⅰ-Ⅻ]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2170-\u217B].*"));//"^[ⅰ-ⅻ]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2460-\u2473].*"));//"^[①-⑳]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2474-\u2487].*"));//"^[⑴-⒇]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2488-\u249B].*"));//"^[⒈-⒛]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u249C-\u24B5].*"));//"^[⒜-⒵]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u24B6-\u24CF].*"));//"^[Ⓐ-Ⓩ]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u24D0-\u24E9].*"));//"^[ⓐ-ⓩ]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u24F5-\u24FE].*"));//"^[⓵-⓾]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2776-\u277F].*"));//"^[❶-❿]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u2780-\u2789].*"));//"^[➀-➉]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u278A-\u2793].*"));//"^[➊-➓]"
        BULLET_PATTERNS.add(Pattern.compile("^[\u326E-\u327B].*"));//"^[㉮-㉻]"
        BULLET_PATTERNS.add(Pattern.compile("^[\uF081-\uF08A].*"));//"^[-]"
        BULLET_PATTERNS.add(Pattern.compile("^[\uF08C-\uF095].*"));//"^[-]"
    }
}
