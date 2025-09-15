package org.opendataloader.pdf.api;

public class FilterConfig {
    private boolean filterHiddenText = true;
    private boolean filterOutOfPage = true;

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
}
