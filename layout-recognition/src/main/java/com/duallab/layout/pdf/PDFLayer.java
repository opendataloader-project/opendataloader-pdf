package com.duallab.layout.pdf;

public enum PDFLayer {
    CONTENT("content"), 
    TABLE_CELLS("table cells"), 
    LIST_ITEMS("list items"), 
    TABLE_CONTENT("table content"), 
    LIST_CONTENT("list content"),
    TEXT_BLOCK_CONTENT("text blocks content"),
    HIDDEN_TEXT("hidden text"),
    HEADER_AND_FOOTER_CONTENT("header and footer content");
    
    private final String value;
    
    PDFLayer(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
