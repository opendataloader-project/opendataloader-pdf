package org.opendataloader.pdf.utils;

import java.util.regex.Pattern;

public class SanitizationRule {
    private final Pattern pattern;
    private final String replacement;
    private final int matchGroup;

    public SanitizationRule(Pattern pattern, String replacement) {
        this(pattern, replacement, 0);
    }

    /** Selects the capturing group to replace, or zero to replace the entire match. */
    public SanitizationRule(Pattern pattern, String replacement, int matchGroup) {
        if (matchGroup < 0 || matchGroup > pattern.matcher("").groupCount()) {
            throw new IllegalArgumentException("Invalid replacement group: " + matchGroup);
        }
        this.pattern = pattern;
        this.replacement = replacement;
        this.matchGroup = matchGroup;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public int getMatchGroup() {
        return matchGroup;
    }
}
