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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration class for content filtering options.
 * Controls filtering of hidden text, out-of-page content, tiny text, and hidden OCGs.
 */
public class FilterConfig {
    private boolean filterHiddenText = false;
    private boolean filterOutOfPage = true;
    private boolean filterTinyText = true;
    private boolean filterHiddenOCG = true;
    private boolean filterSensitiveData = false;
    private final List<SanitizationRule> filterRules = new ArrayList<>();

    /** Default rules */
    private static final List<SanitizationRule> DEFAULT_RULES = new ArrayList<>();
    static {
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "email@example.com"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("[+]\\d+(?:-\\d+)+"),
            "+00-0000-0000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("[A-Z]{1,2}\\d{6,9}"),
            "AA0000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}\\b"),
            "0000-0000-0000-0000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{10,18}\\b"),
            "0000000000000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "0.0.0.0"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b"),
            "0.0.0.0::1"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b"),
            "00:00:00:00:00:00"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{15}\\b"),
            "000000000000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d+)?(/\\S*)?"),
            "https://example.com"
        ));
        //TODO Confirm info about regex for Korean phone, card, resident numbers and etc.
        // Korean Resident Registration Number
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{6}-\\d{7}\\b"),
            "000000-0000000"
        ));
        // Korean phone numbers
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b0\\d{1,2}-\\d{3,4}-\\d{4}\\b"),
            "010-0000-0000"
        ));
        // Korean business registration number
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{5}\\b"),
            "000-00-00000"
        ));
        // Korean bank account numbers
//        DEFAULT_RULES.add(new SanitizationRule(
//            Pattern.compile("\\b\\d{2,4}-\\d{2,3}-\\d{4,6}\\b"),
//            "000-000-000000"
//        ));
        //TODO Confirm info about regex for AWS (maybe create 2 separate rules for AKIA|ASIA)
        // AWS Access Key
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{12,124}\\b"),
            "AKIA0000000000000000"
        ));
        //TODO Confirm info about regex for GitHub (maybe create separate rules for ghp|ghu|gho|ghs|ghr)
        // GitHub Personal Access Token
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\bgh[puors]_[A-Za-z0-9]{10,251}\\b"),
            "ghp_000000000000000000000000000000000000"
        ));
        // GitHub Fine-grained Personal Access Token
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{10,243}\\b"),
            "github_pat_0000000000000000000000_00000000000000000000000000000000000000000000000000000000000"
        ));
        // AWS Secret Key (Finds 40-character, base-64 strings that don't have any base 64 characters immediately before or after).
        // Has to be last rule
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("(?<![A-Za-z0-9/+])[A-Za-z0-9/+]{40}(?![A-Za-z0-9/+])"),
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        ));
    }

    /**
     * Constructor initializing the configuration of filter.
     */
    public FilterConfig() {
        filterRules.addAll(DEFAULT_RULES);
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
