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

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.utils.SanitizationRule;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterConfigTest {

    @Test
    void defaultsKeepInvisibleContentFiltersEnabledButSensitiveDataDisabled() {
        FilterConfig config = new FilterConfig();

        assertFalse(config.isFilterHiddenText());
        assertTrue(config.isFilterOutOfPage());
        assertTrue(config.isFilterTinyText());
        assertTrue(config.isFilterHiddenOCG());
        assertTrue(config.isFilterBackgrounds());
        assertFalse(config.isFilterSensitiveData());
    }

    @Test
    void backgroundFilterCanBeDisabledToKeepVectorGraphics() {
        FilterConfig config = new FilterConfig();
        config.setFilterBackgrounds(false);

        assertFalse(config.isFilterBackgrounds());
    }

    @Test
    void builtInRulesHaveStableIdsInTheirOrder() {
        // examples/sanitize-rules/default-rules.json has the same IDs, in the same order.
        List<String> ids = new FilterConfig().getFilterRules().stream()
            .map(SanitizationRule::getId)
            .collect(Collectors.toList());

        assertEquals(Arrays.asList("email", "phone-intl", "passport", "card", "long-number", "ipv4", "ipv6",
            "mac", "digits-15", "url"), ids);
    }

    @Test
    void createDefaultRulesReturnsANewList() {
        assertNotSame(FilterConfig.createDefaultRules(), FilterConfig.createDefaultRules());
    }

    @Test
    void setFilterRulesAcceptsItsOwnList() {
        FilterConfig config = new FilterConfig();
        int size = config.getFilterRules().size();

        config.setFilterRules(config.getFilterRules());

        assertEquals(size, config.getFilterRules().size());
    }
}
