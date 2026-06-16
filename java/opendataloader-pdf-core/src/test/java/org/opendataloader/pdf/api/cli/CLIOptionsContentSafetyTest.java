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
package org.opendataloader.pdf.api.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class CLIOptionsContentSafetyTest {

    private Config parseArgs(String... args) throws Exception {
        Options options = CLIOptions.defineOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        return CLIOptions.createConfigFromCommandLine(commandLine);
    }

    @Test
    void sanitizeFlagEnablesSensitiveDataFilter() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--sanitize");
        assertTrue(config.getFilterConfig().isFilterSensitiveData(),
                "--sanitize should set filterSensitiveData to true");
    }

    @Test
    void defaultDoesNotEnableSensitiveDataFilter() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp");
        assertFalse(config.getFilterConfig().isFilterSensitiveData(),
                "Without --sanitize, filterSensitiveData should remain false");
    }

    @Test
    void sanitizeWithContentSafetyOffAllStillEnablesSanitize() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--content-safety-off", "all", "--sanitize");
        assertTrue(config.getFilterConfig().isFilterSensitiveData(),
                "--sanitize should set filterSensitiveData=true even when --content-safety-off all is used");
        assertFalse(config.getFilterConfig().isFilterHiddenText(),
                "--content-safety-off all should disable filterHiddenText");
        assertFalse(config.getFilterConfig().isFilterOutOfPage(),
                "--content-safety-off all should disable filterOutOfPage");
        assertFalse(config.getFilterConfig().isFilterTinyText(),
                "--content-safety-off all should disable filterTinyText");
        assertFalse(config.getFilterConfig().isFilterHiddenOCG(),
                "--content-safety-off all should disable filterHiddenOCG");
    }

    @Test
    void contentSafetyOffAllDoesNotTouchSensitiveData() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--content-safety-off", "all");
        assertFalse(config.getFilterConfig().isFilterSensitiveData(),
                "--content-safety-off all should not enable filterSensitiveData (stays false)");
    }

    @Test
    void deprecatedSensitiveDataValueIsAccepted() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--content-safety-off", "sensitive-data");
        assertFalse(config.getFilterConfig().isFilterSensitiveData(),
                "Deprecated sensitive-data value should not enable sanitization");
    }

    @Test
    void deprecatedSensitiveDataValuePrintsWarning() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            parseArgs("--output-dir", "/tmp", "--content-safety-off", "sensitive-data");
            assertTrue(errContent.toString().contains("deprecated"),
                    "Should print a deprecation warning to stderr");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void sanitizeWithDeprecatedSensitiveDataStillEnablesSanitize() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--content-safety-off", "sensitive-data", "--sanitize");
        assertTrue(config.getFilterConfig().isFilterSensitiveData(),
                "--sanitize should win over deprecated --content-safety-off sensitive-data");
    }

    @Test
    void filterHiddenTextDefaultsOff() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp");
        assertFalse(config.getFilterConfig().isFilterHiddenText(),
                "Without --filter-hidden-text, hidden-text filtering should stay off (default)");
    }

    @Test
    void filterHiddenTextOnEnablesFilter() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--filter-hidden-text", "on");
        assertTrue(config.getFilterConfig().isFilterHiddenText(),
                "--filter-hidden-text on should enable hidden-text filtering");
    }

    @Test
    void filterHiddenTextOffDisablesFilter() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--filter-hidden-text", "off");
        assertFalse(config.getFilterConfig().isFilterHiddenText(),
                "--filter-hidden-text off should keep hidden-text filtering off");
    }

    @Test
    void filterHiddenTextValueIsCaseInsensitive() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp", "--filter-hidden-text", "ON");
        assertTrue(config.getFilterConfig().isFilterHiddenText(),
                "--filter-hidden-text value should be case-insensitive");
    }

    @Test
    void filterHiddenTextInvalidValueThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parseArgs("--output-dir", "/tmp", "--filter-hidden-text", "maybe"));
        assertTrue(ex.getMessage().contains("on")  && ex.getMessage().contains("off"),
                "Invalid --filter-hidden-text value should report the supported on/off values");
    }

    @Test
    void filterHiddenTextOnWinsOverContentSafetyOff() throws Exception {
        Config config = parseArgs("--output-dir", "/tmp",
                "--content-safety-off", "hidden-text", "--filter-hidden-text", "on");
        assertTrue(config.getFilterConfig().isFilterHiddenText(),
                "--filter-hidden-text on should take precedence over --content-safety-off hidden-text");
    }
}
