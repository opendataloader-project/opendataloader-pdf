/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.api;

import org.opendataloader.pdf.utils.SanitizationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration class for content filtering options.
 * Controls filtering of hidden text, out-of-page content, tiny text, and hidden OCGs.
 */
public class FilterConfig {
    private boolean filterHiddenText = true;
    private boolean filterOutOfPage = true;
    private boolean filterTinyText = true;
    private boolean filterHiddenOCG = true;
    private boolean filterSensitiveData = true;
    private static final List<SanitizationRule> filterRules = new ArrayList<>();

    /** Default rules */
    static {
        filterRules.add(new SanitizationRule(
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "email@example.com"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("^[+]\\d+(?:-\\d+)+$"),
            "+00-0000-0000"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("[A-Z]{1,2}\\d{6,9}"),
            "AA0000000"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}\\b"),
            "0000-0000-0000-0000"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b\\d{10,18}\\b"),
            "0000000000000000"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "0.0.0.0"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b"),
            "0.0.0.0::1"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b"),
            "00:00:00:00:00:00"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("\\b\\d{15}\\b"),
            "000000000000000"
        ));
        filterRules.add(new SanitizationRule(
            Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d+)?(/\\S*)?"),
            "https://example.com"
        ));
    }

    /**
     * Constructor initializing the configuration of filter.
     */
    public FilterConfig() {}

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
     * @return List<String> of sanitization rules.
     */
    public List<SanitizationRule> getFilterRules() {
        return filterRules;
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
