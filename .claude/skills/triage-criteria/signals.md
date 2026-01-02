# Triage Signal Extraction Methods

This document details how to extract each triage signal from the Java codebase.

## Content Types Overview

PDF content is represented using types from the verapdf-wcag-algs library:

| Type | Package | Description |
|------|---------|-------------|
| `IObject` | `org.verapdf.wcag.algorithms.entities` | Base interface for all content objects |
| `TextChunk` | `org.verapdf.wcag.algorithms.entities.content` | Text fragment with font/color info |
| `LineChunk` | `org.verapdf.wcag.algorithms.entities.content` | Horizontal/vertical line segment |
| `LineArtChunk` | `org.verapdf.wcag.algorithms.entities.content` | Complex line art/shapes |
| `ImageChunk` | `org.verapdf.wcag.algorithms.entities.content` | Image/figure element |
| `TableBorder` | `org.verapdf.wcag.algorithms.entities.tables.tableBorders` | Detected table with borders |

## Signal 1: TableBorder Presence

**Most reliable signal** - detects tables with visible borders.

### Source Class
`org.opendataloader.pdf.processors.DocumentProcessor`

### How It Works
During preprocessing, `LinesPreprocessingConsumer.findTableBorders()` analyzes line segments to detect rectangular table structures. Results are stored in `StaticContainers.getTableBordersCollection()`.

### Extraction Code

```java
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

public boolean hasTableBorders(int pageNumber) {
    TableBordersCollection collection = StaticContainers.getTableBordersCollection();
    SortedSet<TableBorder> tables = collection.getTableBorders(pageNumber);
    return tables != null && !tables.isEmpty();
}
```

### Usage in Existing Code
- `TableBorderProcessor.processTableBorders()` - Line 54
- `AbstractTableProcessor.addTablesToTableCollection()` - Line 65

---

## Signal 2: Suspicious Text Patterns

Detects potential tables by analyzing text chunk spatial relationships.

### Source Class
`org.opendataloader.pdf.processors.AbstractTableProcessor`

### How It Works
Identifies pages where adjacent TextChunks have:
1. Reversed vertical order (current chunk above previous)
2. Same baseline with large horizontal gap (> 3x text height)

### Extraction Code

```java
import org.opendataloader.pdf.processors.AbstractTableProcessor;

public boolean hasSuspiciousTextPatterns(List<List<IObject>> contents) {
    List<Integer> suspiciousPages = AbstractTableProcessor.getPagesWithPossibleTables(contents);
    return !suspiciousPages.isEmpty();
}

public boolean isPageSuspicious(List<List<IObject>> contents, int pageNumber) {
    List<Integer> suspiciousPages = AbstractTableProcessor.getPagesWithPossibleTables(contents);
    return suspiciousPages.contains(pageNumber);
}
```

### Key Constants
From `AbstractTableProcessor`:
```java
private static final double Y_DIFFERENCE_EPSILON = 0.1;  // Baseline tolerance
private static final double X_DIFFERENCE_EPSILON = 3;    // Gap threshold (3x height)
```

### Detection Logic
```java
// From AbstractTableProcessor.areSuspiciousTextChunks()
private static boolean areSuspiciousTextChunks(TextChunk previous, TextChunk current) {
    // Case 1: Out of order (current above previous)
    if (previous.getTopY() < current.getBottomY()) {
        return true;
    }
    // Case 2: Same baseline with large horizontal gap
    if (NodeUtils.areCloseNumbers(previous.getBaseLine(), current.getBaseLine(),
            current.getHeight() * Y_DIFFERENCE_EPSILON)) {
        if (current.getLeftX() - previous.getRightX() >
                current.getHeight() * X_DIFFERENCE_EPSILON) {
            return true;
        }
    }
    return false;
}
```

---

## Signal 3: High LineChunk Ratio

Detects pages with many line elements that could form table borders.

### Source Classes
- `org.opendataloader.pdf.processors.ContentFilterProcessor` (get raw content)
- `org.opendataloader.pdf.processors.DocumentProcessor` (filter LineChunks)

### Extraction Code

```java
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;

public double getLineChunkRatio(List<IObject> pageContents) {
    if (pageContents.isEmpty()) {
        return 0.0;
    }

    long lineCount = pageContents.stream()
        .filter(c -> c instanceof LineChunk || c instanceof LineArtChunk)
        .count();

    return (double) lineCount / pageContents.size();
}

public boolean hasHighLineChunkRatio(List<IObject> pageContents, double threshold) {
    return getLineChunkRatio(pageContents) > threshold;
}
```

### Recommended Threshold
- Default: `0.3` (30% of page content is lines)
- Conservative: `0.2` (20%)
- Performance-focused: `0.4` (40%)

### Note
Lines are filtered out after table processing:
```java
// From DocumentProcessor.processDocument() - Line 138
pageContents = pageContents.stream()
    .filter(x -> !(x instanceof LineChunk))
    .collect(Collectors.toList());
```

---

## Signal 4: Grid Pattern Detection

Analyzes TextChunk spatial layout for grid-like patterns.

### Source Classes
- `org.verapdf.wcag.algorithms.entities.content.TextChunk`
- `org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils`

### Extraction Code

```java
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

public boolean hasGridPattern(List<IObject> pageContents) {
    List<TextChunk> textChunks = pageContents.stream()
        .filter(c -> c instanceof TextChunk)
        .map(c -> (TextChunk) c)
        .filter(c -> !c.isWhiteSpaceChunk() && !c.isEmpty())
        .collect(Collectors.toList());

    // Group by baseline (same row)
    Map<Double, List<TextChunk>> rowGroups = new HashMap<>();
    double tolerance = 5.0; // Points

    for (TextChunk chunk : textChunks) {
        double baseline = chunk.getBaseLine();
        boolean added = false;
        for (Double key : rowGroups.keySet()) {
            if (Math.abs(key - baseline) < tolerance) {
                rowGroups.get(key).add(chunk);
                added = true;
                break;
            }
        }
        if (!added) {
            List<TextChunk> newRow = new ArrayList<>();
            newRow.add(chunk);
            rowGroups.put(baseline, newRow);
        }
    }

    // Check for multiple rows with multiple columns
    int multiColumnRows = 0;
    for (List<TextChunk> row : rowGroups.values()) {
        if (row.size() >= 2) {
            // Sort by X position
            row.sort(Comparator.comparingDouble(TextChunk::getLeftX));
            // Check for large gaps
            for (int i = 1; i < row.size(); i++) {
                double gap = row.get(i).getLeftX() - row.get(i-1).getRightX();
                double avgHeight = (row.get(i).getHeight() + row.get(i-1).getHeight()) / 2;
                if (gap > avgHeight * 3) { // X_DIFFERENCE_EPSILON = 3
                    multiColumnRows++;
                    break;
                }
            }
        }
    }

    // Consider grid pattern if 3+ rows have column structure
    return multiColumnRows >= 3;
}
```

### Key Thresholds
- Baseline tolerance: 5 points (same row detection)
- Gap threshold: 3x text height (column separation)
- Minimum rows: 3 (to distinguish from occasional gaps)

---

## Signal 5: Cluster Table Detection

Uses clustering algorithm to detect borderless tables.

### Source Class
`org.opendataloader.pdf.processors.ClusterTableProcessor`

### Extraction Code

```java
import org.opendataloader.pdf.processors.ClusterTableProcessor;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

public boolean hasClusterTables(List<IObject> pageContents) {
    List<TableBorder> clusterTables =
        ClusterTableProcessor.processClusterDetectionTables(pageContents);
    return !clusterTables.isEmpty();
}
```

### Performance Note
Cluster detection is computationally expensive. Consider:
- Running only if other signals are ambiguous
- Caching results for multi-pass processing
- Skipping for performance-critical applications

### Configuration
Enabled via config:
```java
if (config.isClusterTableMethod()) {
    new ClusterTableProcessor().processTables(contents);
}
```

---

## Combined Triage Function

Example implementation combining all signals:

```java
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.*;

public class TriageProcessor {

    private static final double LINE_CHUNK_THRESHOLD = 0.3;

    public enum TriageResult {
        JAVA,    // Process with Java path
        BACKEND  // Route to Docling backend
    }

    /**
     * Triage all pages and return routing decisions.
     */
    public static Map<Integer, TriageResult> triageAllPages(List<List<IObject>> contents) {
        Map<Integer, TriageResult> results = new HashMap<>();
        List<Integer> suspiciousPages = AbstractTableProcessor.getPagesWithPossibleTables(contents);

        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            results.put(pageNumber, triagePage(contents.get(pageNumber), pageNumber, suspiciousPages));
        }

        return results;
    }

    /**
     * Triage a single page.
     */
    public static TriageResult triagePage(List<IObject> pageContents, int pageNumber,
                                          List<Integer> suspiciousPages) {
        // Signal 1: TableBorder presence (highest priority)
        if (hasTableBorders(pageNumber)) {
            return TriageResult.BACKEND;
        }

        // Signal 2: Suspicious text patterns
        if (suspiciousPages.contains(pageNumber)) {
            return TriageResult.BACKEND;
        }

        // Signal 3: High LineChunk ratio
        if (getLineChunkRatio(pageContents) > LINE_CHUNK_THRESHOLD) {
            return TriageResult.BACKEND;
        }

        // Default: Java path
        return TriageResult.JAVA;
    }

    private static boolean hasTableBorders(int pageNumber) {
        var tables = StaticContainers.getTableBordersCollection().getTableBorders(pageNumber);
        return tables != null && !tables.isEmpty();
    }

    private static double getLineChunkRatio(List<IObject> pageContents) {
        if (pageContents.isEmpty()) return 0.0;
        long lineCount = pageContents.stream()
            .filter(c -> c instanceof LineChunk || c instanceof LineArtChunk)
            .count();
        return (double) lineCount / pageContents.size();
    }
}
```

---

## BoundingBox Coordinate System

All spatial calculations use PDF coordinates:

| Property | Description |
|----------|-------------|
| Origin | Bottom-left corner |
| X axis | Left to right |
| Y axis | Bottom to top |
| Units | PDF points (1/72 inch) |

### BoundingBox Methods
```java
BoundingBox box = content.getBoundingBox();
box.getLeftX();     // Left edge
box.getRightX();    // Right edge
box.getBottomY();   // Bottom edge
box.getTopY();      // Top edge
box.getWidth();     // rightX - leftX
box.getHeight();    // topY - bottomY
box.getPageNumber(); // Page number (0-indexed)
```

### Intersection Check
```java
double overlap = box1.getIntersectionPercent(box2);
// Returns percentage of box1 that overlaps with box2
```
