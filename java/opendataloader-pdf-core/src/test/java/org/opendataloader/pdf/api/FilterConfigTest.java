package org.opendataloader.pdf.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
