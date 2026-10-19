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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads a sanitization rules file (JSON) and makes the list of rules that it gives.
 *
 * <p>The file has one key, {@code rules}: an array of rules, each with {@code id}, {@code pattern}
 * (a Java regular expression), and {@code replacement} (literal text). Only these rules run; the
 * built-in rules do not. The rules are in order of priority.
 *
 * <p>The check is strict. Each of these gives an {@link IllegalArgumentException} with the reason, and the rule
 * or the key: an unknown or missing key; a rule ID that is repeated or has a wrong format; a pattern that is
 * empty or does not compile; a replacement that is empty or has a control character; a value over a limit;
 * no rules. Lengths are counted in Java {@code char} units. Values from the file are quoted in the message with
 * at most 64 characters, and characters other than printable ASCII are escaped. Parser messages are escaped in
 * the same way, with at most 200 characters.
 */
public final class SanitizationRulesLoader {
    /** The maximum size of a rules file, in bytes (1 MiB). */
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    /** The maximum number of rules in the {@code rules} array. */
    public static final int MAX_RULES = 200;
    /** The maximum length of a pattern. */
    public static final int MAX_PATTERN_LENGTH = 5000;
    /** The maximum length of a replacement. */
    public static final int MAX_REPLACEMENT_LENGTH = 200;

    private static final String RULES_KEY = "rules";
    private static final String ID_KEY = "id";
    private static final String PATTERN_KEY = "pattern";
    private static final String REPLACEMENT_KEY = "replacement";
    private static final List<String> FILE_KEYS = Collections.singletonList(RULES_KEY);
    private static final List<String> RULE_KEYS = Arrays.asList(ID_KEY, PATTERN_KEY, REPLACEMENT_KEY);
    private static final Pattern ID_FORMAT = Pattern.compile("[a-z0-9-]{1,64}");
    private static final int MAX_QUOTED_LENGTH = 64;
    private static final int MAX_DETAIL_LENGTH = 200;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private SanitizationRulesLoader() {
    }

    /**
     * Reads a rules file and makes the list of rules that it gives.
     *
     * @param file the rules file.
     * @return the rules, in order of priority.
     * @throws IllegalArgumentException if the file cannot be read or is not valid.
     */
    public static List<SanitizationRule> load(Path file) {
        try {
            return parse(read(file));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid sanitize rules file '" + file + "': "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Makes the list of rules from the content of a rules file.
     *
     * @param json the content of the rules file.
     * @return the rules, in order of priority.
     * @throws IllegalArgumentException if the content is not valid.
     */
    static List<SanitizationRule> parse(byte[] json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("the file must contain one JSON object");
        }
        checkKeys(root, FILE_KEYS, "the file");
        List<SanitizationRule> rules = readRules(root);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("'" + RULES_KEY + "' has no rules, so sanitization would change nothing");
        }
        return Collections.unmodifiableList(rules);
    }

    private static byte[] read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("the file does not exist or is not a regular file");
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] content = input.readNBytes(MAX_FILE_BYTES + 1);
            if (content.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("the file is larger than " + MAX_FILE_BYTES + " bytes");
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalArgumentException("the file cannot be read (" + exception.getClass().getSimpleName()
                    + ")", exception);
        }
    }

    private static JsonNode readTree(byte[] json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            JsonLocation location = exception.getLocation();
            String where = location == null ? ""
                    : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
            throw new IllegalArgumentException("the file is not valid JSON" + where + ": "
                    + escape(exception.getOriginalMessage(), MAX_DETAIL_LENGTH), exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("the file is not valid JSON", exception);
        }
    }

    private static List<SanitizationRule> readRules(JsonNode root) {
        List<SanitizationRule> rules = new ArrayList<>();
        JsonNode node = root.get(RULES_KEY);
        if (node == null) {
            throw new IllegalArgumentException("the file has no '" + RULES_KEY + "' key");
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + RULES_KEY + "' must be an array of rule objects");
        }
        if (node.size() > MAX_RULES) {
            throw new IllegalArgumentException("'" + RULES_KEY + "' has " + node.size() + " rules; the maximum is "
                    + MAX_RULES);
        }
        Set<String> ids = new LinkedHashSet<>();
        int number = 0;
        for (JsonNode item : node) {
            number++;
            String where = "rule " + number;
            if (!item.isObject()) {
                throw new IllegalArgumentException(where + " must be a JSON object");
            }
            String id = readText(item, ID_KEY, where);
            if (!ID_FORMAT.matcher(id).matches()) {
                throw new IllegalArgumentException(where + ": the ID " + quote(id)
                        + " must have 1 to 64 characters from a-z, 0-9, and '-'");
            }
            where = "rule '" + id + "'";
            checkKeys(item, RULE_KEYS, where);
            if (!ids.add(id)) {
                throw new IllegalArgumentException(where + ": the ID is used more than once");
            }
            rules.add(new SanitizationRule(id, readPattern(item, where), readReplacement(item, where)));
        }
        return rules;
    }

    private static Pattern readPattern(JsonNode rule, String where) {
        String pattern = readText(rule, PATTERN_KEY, where);
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException(where + ": the pattern is empty");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException(where + ": the pattern has " + pattern.length()
                    + " characters; the maximum is " + MAX_PATTERN_LENGTH);
        }
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(where + ": the pattern does not compile: "
                    + escape(exception.getDescription(), MAX_DETAIL_LENGTH) + " (index " + exception.getIndex() + ")",
                    exception);
        }
    }

    private static String readReplacement(JsonNode rule, String where) {
        String replacement = readText(rule, REPLACEMENT_KEY, where);
        if (replacement.isEmpty()) {
            throw new IllegalArgumentException(where + ": the replacement is empty");
        }
        if (replacement.length() > MAX_REPLACEMENT_LENGTH) {
            throw new IllegalArgumentException(where + ": the replacement has " + replacement.length()
                    + " characters; the maximum is " + MAX_REPLACEMENT_LENGTH);
        }
        if (replacement.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(where + ": the replacement contains a control character,"
                    + " for example a line break or a tab");
        }
        return replacement;
    }

    private static String readText(JsonNode object, String key, String where) {
        JsonNode node = object.get(key);
        if (node == null) {
            throw new IllegalArgumentException(where + ": '" + key + "' is missing");
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(where + ": '" + key + "' must be a string");
        }
        return node.textValue();
    }

    private static void checkKeys(JsonNode object, List<String> allowed, String where) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(where + " has an unknown key " + quote(name) + ". Allowed keys: "
                        + String.join(", ", allowed));
            }
        }
    }

    /**
     * Quotes a value from the file for a message. The result has at most 64 characters of the value,
     * and each character other than printable ASCII is written as a Unicode escape, so that the message
     * cannot send control sequences to a terminal.
     */
    private static String quote(String value) {
        return "'" + escape(value, MAX_QUOTED_LENGTH) + "'";
    }

    /**
     * Makes text from the file, or a parser message that can contain it, safe for a message.
     * The result has at most the given number of characters of the text, and each character
     * other than printable ASCII is written as a Unicode escape.
     */
    private static String escape(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        int length = Math.min(value.length(), maxLength);
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c < 0x7f) {
                escaped.append(c);
            } else {
                escaped.append(String.format("\\u%04x", (int) c));
            }
        }
        if (value.length() > length) {
            escaped.append("...");
        }
        return escaped.toString();
    }
}
