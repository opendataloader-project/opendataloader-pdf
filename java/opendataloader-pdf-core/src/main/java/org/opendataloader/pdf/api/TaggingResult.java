package org.opendataloader.pdf.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.verapdf.pd.PDDocument;

import java.io.IOException;

/**
 * Result of {@link AutoTagger#tag}. Contains the tagged PDF document in-memory
 * and processing timing metadata.
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources.
 * The caller is responsible for closing this result, which releases the
 * underlying PDDocument resources.
 */
public class TaggingResult implements AutoCloseable {

    private final PDDocument document;
    private final long extractionNs;
    private final long taggingNs;
    private final JsonNode hybridTimings;

    public TaggingResult(PDDocument document, long extractionNs, long taggingNs, JsonNode hybridTimings) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        this.document = document;
        this.extractionNs = extractionNs;
        this.taggingNs = taggingNs;
        this.hybridTimings = hybridTimings;
    }

    /** The tagged PDF document. Do not close this directly — close the TaggingResult instead. */
    public PDDocument getDocument() {
        return document;
    }

    /** Time spent on extraction (parsing + layout + content extraction) in nanoseconds. */
    public long getExtractionNs() {
        return extractionNs;
    }

    /** Time spent on auto-tagging (structure tree creation) in nanoseconds. */
    public long getTaggingNs() {
        return taggingNs;
    }

    /** Per-step hybrid server timings, or null if hybrid mode was not used. */
    public JsonNode getHybridTimings() {
        return hybridTimings;
    }

    /**
     * Save the tagged PDF to a file.
     *
     * @param outputPath the output file path
     */
    public void saveTo(String outputPath) throws IOException {
        document.saveAs(outputPath);
    }

    @Override
    public void close() {
        if (document != null) {
            document.close();
        }
    }
}
