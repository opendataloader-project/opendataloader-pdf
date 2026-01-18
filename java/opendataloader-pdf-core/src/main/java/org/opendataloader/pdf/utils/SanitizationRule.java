package org.opendataloader.pdf.utils;

import java.util.regex.Pattern;

public class SanitizationRule {
    private final Pattern pattern;
    private final String replacement;

    public SanitizationRule(Pattern pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getReplacement() {
        return replacement;
    }
}
