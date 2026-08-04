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

import java.util.HashSet;
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
    private static final Set<Pattern> BULLET_REGEXES = new HashSet<>();
    private static final Set<String> ARABIC_NUMBER_REGEXES = new HashSet<>();
    private static final String KOREAN_NUMBERS_REGEX = "[가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후그느드르므브스으즈츠크트프흐기니디리미비시이지치키티피히]";
    /** Regular expression for Korean chapter patterns like 제1장, 제2조, 제3절. */
    public static final String KOREAN_CHAPTER_REGEX = "^(제\\d+[장조절])";

    /**
     * Roman numeral section title: "I. INTRODUCTION", "III. METHODOLOGY", etc.
     */
    private static final Pattern ROMAN_SECTION_PATTERN =
            Pattern.compile("^(XII|VIII|VII|XI|VI|IV|IX|III|II|X|V|I)\\.\\s+[A-Za-z0-9].*$");

    /**
     * Alpha subsection title: "A. Network Architecture", "B. Experimental Results", etc.
     */
    private static final Pattern ALPHA_SUBSECTION_PATTERN =
            Pattern.compile("^[A-Z]\\.\\s+[A-Za-z0-9].*$");

    /**
     * Numeric section title: "1. Introduction", "2. Related Work", "1.1 Overview", etc.
     */
    private static final Pattern NUMERIC_SECTION_PATTERN =
            Pattern.compile("^(\\d+\\.|\\d+\\.\\d+|\\d+\\))\\s+[A-Za-z0-9].*$");

    /**
     * Standard unnumbered major section headers across academic papers and technical reports.
     */
    private static final Pattern STANDARD_SECTION_NAMES_PATTERN =
            Pattern.compile("^(ABSTRACT|INTRODUCTION|RELATED WORK|BACKGROUND|PRELIMINARIES|METHOD|METHODOLOGY|MODEL|PROPOSED APPROACH|EXPERIMENTS|EXPERIMENTAL SETUP|RESULTS|DISCUSSION|EVALUATION|LIMITATIONS|CONCLUSION|CONCLUSIONS|FUTURE WORK|REFERENCES|BIBLIOGRAPHY|APPENDIX|APPENDICES|ACKNOWLEDGMENT|ACKNOWLEDGMENTS)\\s*[:\\.\\-—–]?$", Pattern.CASE_INSENSITIVE);

    /**
     * Checks if a text line is a Roman numeral section title (e.g. "I. INTRODUCTION", "III. METHODOLOGY").
     *
     * @param line the text line to check
     * @return true if the line is a Roman numeral section title, false otherwise
     */
    public static boolean isRomanSectionTitle(TextLine line) {
        if (line == null || line.getValue() == null) return false;
        String val = line.getValue().trim();
        return !isAuthorAffiliationLine(val) && ROMAN_SECTION_PATTERN.matcher(val).find();
    }

    /**
     * Checks if a text line is an alphabetical subsection title (e.g. "A. Network Architecture", "B. Experimental Results").
     *
     * @param line the text line to check
     * @return true if the line is an alphabetical subsection title, false otherwise
     */
    public static boolean isAlphaSubsectionTitle(TextLine line) {
        if (line == null || line.getValue() == null) return false;
        String val = line.getValue().trim();
        return !isAuthorAffiliationLine(val) && ALPHA_SUBSECTION_PATTERN.matcher(val).find();
    }

    /**
     * Checks if a text line is a numeric section title (e.g. "1. Introduction", "2. Related Work", "1.1 Overview").
     *
     * @param line the text line to check
     * @return true if the line is a numeric section title, false otherwise
     */
    public static boolean isNumericSectionTitle(TextLine line) {
        if (line == null || line.getValue() == null) return false;
        String val = line.getValue().trim();
        return !isAuthorAffiliationLine(val) && NUMERIC_SECTION_PATTERN.matcher(val).find();
    }

    /**
     * Checks if a text line is a section or subsection title across standard academic and technical document conventions,
     * e.g. {@code "I. INTRODUCTION"}, {@code "B. Experimental Results"}, {@code "1. Introduction"}, or {@code "METHODOLOGY"}.
     * Used by {@link org.opendataloader.pdf.processors.HeadingProcessor} to promote these lines to headings.
     *
     * @param line the text line to check
     * @return true if the line is a recognized section or subsection title, false otherwise
     */
    public static boolean isIeeeSectionTitle(TextLine line) {
        if (line == null || line.getValue() == null) {
            return false;
        }
        String val = line.getValue().trim();
        if (isAuthorAffiliationLine(val)) {
            return false;
        }
        if (val.length() > 80 || val.split("\\s+").length > 10) {
            return false;
        }
        if (val.matches("^(A\\.|\\d+\\.)\\s+(This|The|We|In|Here|Our|For|With|By|To|From)\\b.*")) {
            return false;
        }

        return ROMAN_SECTION_PATTERN.matcher(val).matches()
            || ALPHA_SUBSECTION_PATTERN.matcher(val).matches()
            || NUMERIC_SECTION_PATTERN.matcher(val).matches()
            || STANDARD_SECTION_NAMES_PATTERN.matcher(val).matches();
    }

    /**
     * Checks if a text string is an author affiliation or contact metadata line (e.g. emails, web links, or author address blocks).
     *
     * @param text the text string to check
     * @return true if the text contains email, URL, or author list metadata, false otherwise
     */
    private static boolean isAuthorAffiliationLine(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        // Email address or URL in line (e.g. {user1, user2}@domain.edu or https://...)
        if (trimmed.contains("@") || trimmed.toLowerCase().contains("http:") || trimmed.toLowerCase().contains("https:")) {
            return true;
        }
        // Long author block lines with multiple comma-separated names and affiliations
        if (trimmed.length() > 100 && trimmed.chars().filter(ch -> ch == ',').count() >= 3) {
            return true;
        }
        return false;
    }

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
        for (Pattern regex : BULLET_REGEXES) {
            if (regex.matcher(value).find()) {
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
        for (Pattern regex : BULLET_REGEXES) {
            if (regex.matcher(value).find()) {
                return regex.pattern();
            }
        }
        return null;
    }

    static {
        ARABIC_NUMBER_REGEXES.add("^\\d+[ \\.\\]\\)>].*");
        BULLET_REGEXES.add(Pattern.compile("^\\(\\d+\\)"));
        ARABIC_NUMBER_REGEXES.add("^<\\d+>.*");
        ARABIC_NUMBER_REGEXES.add("^\\[\\d+\\].*");
        ARABIC_NUMBER_REGEXES.add("^{\\d+}.*");
        ARABIC_NUMBER_REGEXES.add("^【\\d+】.*");
        BULLET_REGEXES.add(Pattern.compile("^\\d+[\\.\\)]\\s+"));
        BULLET_REGEXES.add(Pattern.compile("^[ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ][\\.\\)\\]>]"));
        BULLET_REGEXES.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "\\..+"));
        BULLET_REGEXES.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "[)\\]>]"));
        BULLET_REGEXES.add(Pattern.compile("^" + KOREAN_NUMBERS_REGEX + "(-\\d+)"));
        BULLET_REGEXES.add(Pattern.compile("^\\(" + KOREAN_NUMBERS_REGEX + "\\)"));
        BULLET_REGEXES.add(Pattern.compile("^<" + KOREAN_NUMBERS_REGEX + ">"));
        BULLET_REGEXES.add(Pattern.compile("^\\[" + KOREAN_NUMBERS_REGEX + "\\]"));
        BULLET_REGEXES.add(Pattern.compile("^[{]" + KOREAN_NUMBERS_REGEX + "[}]"));
        BULLET_REGEXES.add(Pattern.compile(KOREAN_CHAPTER_REGEX));
        BULLET_REGEXES.add(Pattern.compile("^법\\.(제\\d+조)"));
        BULLET_REGEXES.add(Pattern.compile("^[\u0049]\\."));//"^[Ⅰ-Ⅻ]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2160-\u216B]"));//"^[Ⅰ-Ⅻ]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2170-\u217B]"));//"^[ⅰ-ⅻ]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2460-\u2473]"));//"^[①-⑳]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2474-\u2487]"));//"^[⑴-⒇]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2488-\u249B]"));//"^[⒈-⒛]"
        BULLET_REGEXES.add(Pattern.compile("^[\u249C-\u24B5]"));//"^[⒜-⒵]"
        BULLET_REGEXES.add(Pattern.compile("^[\u24B6-\u24CF]"));//"^[Ⓐ-Ⓩ]"
        BULLET_REGEXES.add(Pattern.compile("^[\u24D0-\u24E9]"));//"^[ⓐ-ⓩ]"
        BULLET_REGEXES.add(Pattern.compile("^[\u24F5-\u24FE]"));//"^[⓵-⓾]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2776-\u277F]"));//"^[❶-❿]"
        BULLET_REGEXES.add(Pattern.compile("^[\u2780-\u2789]"));//"^[➀-➉]"
        BULLET_REGEXES.add(Pattern.compile("^[\u278A-\u2793]"));//"^[➊-➓]"
        BULLET_REGEXES.add(Pattern.compile("^[\u326E-\u327B]"));//"^[㉮-㉻]"
        BULLET_REGEXES.add(Pattern.compile("^[\uF081-\uF08A]"));//"^[-]"
        BULLET_REGEXES.add(Pattern.compile("^[\uF08C-\uF095]"));//"^[-]"
    }
}
