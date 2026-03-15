package org.opendataloader.pdf.autotagging;

import java.util.Objects;

public class OperatorStreamKey {
    private final int pageNumber;
    private final String xObjectName;

    public OperatorStreamKey(int pageNumber, String xObjectName) {
        this.pageNumber = pageNumber;
        this.xObjectName = xObjectName;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getXObjectName() {
        return xObjectName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OperatorStreamKey that = (OperatorStreamKey) o;
        return pageNumber == that.pageNumber &&
            Objects.equals(xObjectName, that.xObjectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageNumber, xObjectName);
    }
}
