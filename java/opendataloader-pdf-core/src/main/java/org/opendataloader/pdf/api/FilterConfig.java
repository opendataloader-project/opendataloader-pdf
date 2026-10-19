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
package org.opendataloader.pdf.api;

import org.opendataloader.pdf.utils.SanitizationRule;
import org.opendataloader.pdf.utils.SanitizationRulesLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration class for content filtering options.
 * Controls filtering of hidden text, out-of-page content, tiny text, hidden OCGs,
 * and background vector graphics.
 */
public class FilterConfig {
    private boolean filterHiddenText = false;
    private boolean filterOutOfPage = true;
    private boolean filterTinyText = true;
    private boolean filterHiddenOCG = true;
    private boolean filterBackgrounds = true;
    private boolean filterSensitiveData = false;
    private final List<SanitizationRule> filterRules;

    /**
     * Creates the built-in sanitization rules, in order of priority. All rules run on the original text.
     * When two matches have the same start and the same length, the match of the earlier rule is kept.
     * Each rule has an ID. examples/sanitize-rules/default-rules.json has the same rules, for a sanitize rules file.
     *
     * @return a new, modifiable list of the built-in rules.
     */
    public static List<SanitizationRule> createDefaultRules() {
        List<SanitizationRule> rules = new ArrayList<>();
        rules.add(new SanitizationRule(
            "email",
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "email@example.com"
        ));
        rules.add(new SanitizationRule(
            "phone-intl",
            Pattern.compile("[+]\\d+(?:-\\d+)+"),
            "+00-0000-0000"
        ));
        rules.add(new SanitizationRule(
            "passport",
            Pattern.compile("[A-Z]{1,2}\\d{6,9}"),
            "AA0000000"
        ));
        rules.add(new SanitizationRule(
            "card",
            Pattern.compile("\\b\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}\\b"),
            "0000-0000-0000-0000"
        ));
        rules.add(new SanitizationRule(
            "long-number",
            Pattern.compile("\\b\\d{10,18}\\b"),
            "0000000000000000"
        ));
        rules.add(new SanitizationRule(
            "ipv4",
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "0.0.0.0"
        ));
        String h16 = "[0-9a-fA-F]{1,4}";
        String octet = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
        String ipv4 = "(?:" + octet + "\\.){3}" + octet;
        String ls32 = "(?:" + h16 + ":" + h16 + "|" + ipv4 + ")";
        // RFC 3986 section 3.2.2: the final 32 bits may use dotted IPv4 notation.
        String ipv6 = "(?:" + h16 + ":){6}" + ls32
            + "|::(?:" + h16 + ":){5}" + ls32
            + "|(?:" + h16 + ")?::(?:" + h16 + ":){4}" + ls32
            + "|(?:(?:" + h16 + ":){0,1}" + h16 + ")?::(?:" + h16 + ":){3}" + ls32
            + "|(?:(?:" + h16 + ":){0,2}" + h16 + ")?::(?:" + h16 + ":){2}" + ls32
            + "|(?:(?:" + h16 + ":){0,3}" + h16 + ")?::" + h16 + ":" + ls32
            + "|(?:(?:" + h16 + ":){0,4}" + h16 + ")?::" + ls32
            + "|(?:(?:" + h16 + ":){0,5}" + h16 + ")?::" + h16
            + "|(?:(?:" + h16 + ":){0,6}" + h16 + ")?::";
        // The RFC 3986 grammar alone rejects ordinary timestamps and ratios. The boundaries only
        // keep a match from starting or ending inside a word, so a label, a trailing colon, a
        // port, a hyphen, or a dotted suffix stays outside the replaced span.
        rules.add(new SanitizationRule(
            "ipv6",
            Pattern.compile("(?<!\\w)(" + ipv6 + ")(?!\\w)"),
            "0.0.0.0::1", 1
        ));
        rules.add(new SanitizationRule(
            "mac",
            Pattern.compile("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b"),
            "00:00:00:00:00:00"
        ));
        rules.add(new SanitizationRule(
            "digits-15",
            Pattern.compile("\\b\\d{15}\\b"),
            "000000000000000"
        ));
        rules.add(new SanitizationRule(
            "url",
            Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d+)?(/\\S*)?"),
            "https://example.com"
        ));
        return rules;
    }

    /**
     * Constructor initializing the configuration of filter.
     */
    public FilterConfig() {
        this.filterRules = new ArrayList<>(createDefaultRules());
    }

    /**
     * Enables or disables filter of hidden text.
     *
     * @param filterHiddenText true to enable filter, false to disable.
     */
    public void setFilterHiddenText(boolean filterHiddenText) {
        this.filterHiddenText = filterHiddenText;
    }

    /**
     * Checks if the processor should attempt to find and extract hidden text.
     *
     * @return true if hidden text is filtered, false otherwise.
     */
    public boolean isFilterHiddenText() {
        return filterHiddenText;
    }

    /**
     * Enables or disables checking content that exceeds MediaBox or CropBox.
     *
     * @param filterOutOfPage true to enable, false to disable.
     */
    public void setFilterOutOfPage(boolean filterOutOfPage) {
        this.filterOutOfPage = filterOutOfPage;
    }

    /**
     * Checks if the processor should filter out of page content.
     *
     * @return true if filter is enabled, false otherwise.
     */
    public boolean isFilterOutOfPage() {
        return filterOutOfPage;
    }

    /**
     * Checks if the processor should filter out tiny text.
     *
     * @return true if filter is enabled, false otherwise.
     */
    public boolean isFilterTinyText() {
        return filterTinyText;
    }

    /**
     * Enables or disables filter of tiny text.
     *
     * @param filterTinyText true to enable filter, false to disable.
     */
    public void setFilterTinyText(boolean filterTinyText) {
        this.filterTinyText = filterTinyText;
    }

    /**
     * Checks if the processor should filter out hidden OCGs.
     *
     * @return true if filter is enabled, false otherwise.
     */
    public boolean isFilterHiddenOCG() {
        return filterHiddenOCG;
    }

    /**
     * Enables or disables filter of hidden OCGs.
     *
     * @param filterHiddenOCG true to enable filter, false to disable.
     */
    public void setFilterHiddenOCG(boolean filterHiddenOCG) {
        this.filterHiddenOCG = filterHiddenOCG;
    }

    /**
     * Checks if the processor should remove page-sized vector graphics detected
     * as backgrounds.
     *
     * @return true if filter is enabled, false otherwise.
     */
    public boolean isFilterBackgrounds() {
        return filterBackgrounds;
    }

    /**
     * Enables or disables removal of vector graphics detected as backgrounds.
     *
     * <p>Background detection is a size heuristic, so a large chart or diagram
     * drawn with vector operators can be classified as a background and dropped
     * before tagging. Disable this filter to keep every {@code LineArtChunk} in
     * the content list, at the cost of also keeping real page backgrounds.
     *
     * @param filterBackgrounds true to enable filter, false to disable.
     */
    public void setFilterBackgrounds(boolean filterBackgrounds) {
        this.filterBackgrounds = filterBackgrounds;
    }

    /**
     * Checks if the processor should filter out sensitive data.
     *
     * @return true if filter is enabled, false otherwise.
     */
    public boolean isFilterSensitiveData() {
        return filterSensitiveData;
    }

    /**
     * Enables or disables filter of sensitive data.
     *
     * @param filterSensitiveData true to enable filter, false to disable.
     */
    public void setFilterSensitiveData(boolean filterSensitiveData) {
        this.filterSensitiveData = filterSensitiveData;
    }

    /**
     * Gets custom filter sanitization rules.
     *
     * @return List of sanitization rules.
     */
    public List<SanitizationRule> getFilterRules() {
        return filterRules;
    }

    /**
     * Replaces all sanitization rules, including the built-in rules.
     *
     * @param rules the new rules, in order of priority.
     */
    public void setFilterRules(List<SanitizationRule> rules) {
        List<SanitizationRule> copy = new ArrayList<>(rules);
        filterRules.clear();
        filterRules.addAll(copy);
    }

    /**
     * Replaces all sanitization rules, including the built-in rules, with the rules of a sanitize rules
     * file (JSON, see {@link SanitizationRulesLoader}). Only the rules of the file run. Rules that were
     * added before with {@link #addFilterRule(String, String)} are removed. If the file is not valid,
     * the rules do not change.
     *
     * @param file the rules file.
     * @throws IllegalArgumentException if the file cannot be read or is not valid.
     */
    public void loadFilterRules(Path file) {
        setFilterRules(SanitizationRulesLoader.load(file));
    }

    /**
     * Add custom filter sanitization rule.
     *
     * @param pattern pattern string.
     * @param replacement pattern replacement string.
     */
    public void addFilterRule(String pattern, String replacement) {
        filterRules.add(new SanitizationRule(Pattern.compile(pattern), replacement));
    }

    /**
     * Remove filter sanitization rule.
     *
     * @param pattern pattern string.
     */
    public void removeFilterRule(String pattern) {
        filterRules.removeIf(rule -> rule.getPattern().pattern().equals(pattern));
    }
}
