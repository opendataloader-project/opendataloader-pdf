package org.opendataloader.pdf.api;

public class FilterConfig {
    private boolean filterHiddenText = true;
    private boolean filterOutOfPage = true;
    private boolean filterTinyText = true;
    private boolean filterHiddenOCG = true;

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
}
