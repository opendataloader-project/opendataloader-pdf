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

import java.util.regex.Pattern;

/**
 * One sanitization rule: a pattern and the literal text that replaces each match.
 */
public final class SanitizationRule {
    private final String id;
    private final Pattern pattern;
    private final String replacement;
    private final int matchGroup;

    /**
     * Creates a rule without an ID that replaces the entire match.
     *
     * @param pattern the pattern to find.
     * @param replacement the literal text that replaces each match.
     */
    public SanitizationRule(Pattern pattern, String replacement) {
        this(null, pattern, replacement, 0);
    }

    /**
     * Creates a rule without an ID that replaces one capturing group.
     *
     * @param pattern the pattern to find.
     * @param replacement the literal text that replaces the group.
     * @param matchGroup the capturing group to replace, or zero to replace the entire match.
     */
    public SanitizationRule(Pattern pattern, String replacement, int matchGroup) {
        this(null, pattern, replacement, matchGroup);
    }

    /**
     * Creates a rule with an ID that replaces the entire match.
     *
     * @param id the ID of the rule, or null.
     * @param pattern the pattern to find.
     * @param replacement the literal text that replaces each match.
     */
    public SanitizationRule(String id, Pattern pattern, String replacement) {
        this(id, pattern, replacement, 0);
    }

    /**
     * Creates a rule with an ID that replaces one capturing group.
     *
     * @param id the ID of the rule, or null.
     * @param pattern the pattern to find.
     * @param replacement the literal text that replaces the group.
     * @param matchGroup the capturing group to replace, or zero to replace the entire match.
     */
    public SanitizationRule(String id, Pattern pattern, String replacement, int matchGroup) {
        if (matchGroup < 0 || matchGroup > pattern.matcher("").groupCount()) {
            throw new IllegalArgumentException("Invalid match group: " + matchGroup);
        }
        this.id = id;
        this.pattern = pattern;
        this.replacement = replacement;
        this.matchGroup = matchGroup;
    }

    /**
     * Gets the ID of the rule.
     *
     * @return the ID, or null if the rule has no ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the pattern of the rule.
     *
     * @return the pattern.
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * Gets the literal text that replaces each match.
     *
     * @return the replacement text.
     */
    public String getReplacement() {
        return replacement;
    }

    /**
     * Gets the capturing group that is replaced.
     *
     * @return the group, or zero if the entire match is replaced.
     */
    public int getMatchGroup() {
        return matchGroup;
    }
}
