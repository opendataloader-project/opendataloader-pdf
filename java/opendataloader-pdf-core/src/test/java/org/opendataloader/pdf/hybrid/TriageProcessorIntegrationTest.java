/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.processors.ContentFilterProcessor;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Integration test for TriageProcessor accuracy using real benchmark PDFs.
 *
 * <p>This test loads actual PDF files and measures triage accuracy
 * against known ground truth (documents with tables).
 */
public class TriageProcessorIntegrationTest {

    private static final Path BENCHMARK_PDF_DIR = Paths.get("../../tests/benchmark/pdfs");

    /**
     * Documents that have tables (from TriageProcessorBenchmarkTest).
     */
    private static final Set<String> DOCUMENTS_WITH_TABLES = new HashSet<>(Arrays.asList(
        "01030000000045", "01030000000046", "01030000000047", "01030000000051",
        "01030000000052", "01030000000053", "01030000000064", "01030000000078",
        "01030000000081", "01030000000082", "01030000000083", "01030000000084",
        "01030000000088", "01030000000089", "01030000000090", "01030000000110",
        "01030000000116", "01030000000117", "01030000000119", "01030000000120",
        "01030000000121", "01030000000122", "01030000000127", "01030000000128",
        "01030000000130", "01030000000132", "01030000000146", "01030000000147",
        "01030000000149", "01030000000150", "01030000000165", "01030000000166",
        "01030000000170", "01030000000178", "01030000000180", "01030000000182",
        "01030000000187", "01030000000188", "01030000000189", "01030000000190",
        "01030000000197", "01030000000200"
    ));

    private static boolean benchmarkPdfsAvailable = false;

    /**
     * Minimum file size to consider a PDF valid (not a Git LFS stub).
     * Git LFS stubs are typically small text files (~130 bytes).
     */
    private static final long MIN_PDF_SIZE = 1024;

    @BeforeAll
    static void checkBenchmarkDir() {
        if (!Files.exists(BENCHMARK_PDF_DIR) || !Files.isDirectory(BENCHMARK_PDF_DIR)) {
            System.out.println("Benchmark PDF directory not found: " + BENCHMARK_PDF_DIR.toAbsolutePath());
            System.out.println("Skipping integration tests. Run 'git lfs pull' to fetch test PDFs.");
            return;
        }

        // Check if PDFs are actual files (not Git LFS stubs)
        File samplePdf = BENCHMARK_PDF_DIR.resolve("01030000000001.pdf").toFile();
        if (samplePdf.exists() && samplePdf.length() > MIN_PDF_SIZE) {
            benchmarkPdfsAvailable = true;
        } else {
            System.out.println("Benchmark PDFs appear to be Git LFS stubs (size: " +
                (samplePdf.exists() ? samplePdf.length() : 0) + " bytes)");
            System.out.println("Skipping integration tests. Run 'git lfs pull' to fetch actual PDFs.");
        }
    }

    @Test
    public void testTriageAccuracyOnBenchmarkPDFs() throws IOException {
        if (!benchmarkPdfsAvailable) {
            System.out.println("Skipping test: benchmark PDFs not available");
            return;
        }

        File[] pdfFiles = BENCHMARK_PDF_DIR.toFile().listFiles((dir, name) -> name.endsWith(".pdf"));
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("No PDF files found in benchmark directory");
            return;
        }

        int tp = 0, fp = 0, fn = 0, tn = 0;
        List<String> falseNegatives = new ArrayList<>();
        List<String> falsePositives = new ArrayList<>();

        for (File pdfFile : pdfFiles) {
            String docId = pdfFile.getName().replace(".pdf", "");
            boolean hasTable = DOCUMENTS_WITH_TABLES.contains(docId);

            try {
                TriageDecision decision = triageDocument(pdfFile);
                boolean predictedTable = (decision == TriageDecision.BACKEND);

                if (hasTable && predictedTable) {
                    tp++;
                } else if (!hasTable && predictedTable) {
                    fp++;
                    falsePositives.add(docId);
                } else if (hasTable && !predictedTable) {
                    fn++;
                    falseNegatives.add(docId);
                } else {
                    tn++;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + docId + ": " + e.getMessage());
            }
        }

        // Calculate metrics
        double precision = tp + fp > 0 ? (double) tp / (tp + fp) : 0;
        double recall = tp + fn > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double accuracy = (double) (tp + tn) / (tp + tn + fp + fn);

        // Print results
        System.out.println("\n========== Triage Accuracy Results ==========");
        System.out.println("Total documents: " + (tp + tn + fp + fn));
        System.out.println("Documents with tables: " + DOCUMENTS_WITH_TABLES.size());
        System.out.println();
        System.out.println("Confusion Matrix:");
        System.out.println("  TP (correct BACKEND): " + tp);
        System.out.println("  TN (correct JAVA):    " + tn);
        System.out.println("  FP (wrong BACKEND):   " + fp);
        System.out.println("  FN (wrong JAVA):      " + fn);
        System.out.println();
        System.out.printf("Precision: %.2f%% (%d/%d)%n", precision * 100, tp, tp + fp);
        System.out.printf("Recall:    %.2f%% (%d/%d)%n", recall * 100, tp, tp + fn);
        System.out.printf("F1 Score:  %.2f%%%n", f1 * 100);
        System.out.printf("Accuracy:  %.2f%%%n", accuracy * 100);
        System.out.println();

        if (!falseNegatives.isEmpty()) {
            System.out.println("False Negatives (missed tables): " + falseNegatives);
        }
        if (!falsePositives.isEmpty()) {
            System.out.println("False Positives (wrong detection): " + falsePositives);
        }
        System.out.println("==============================================\n");

        // Assertions - prioritize recall (minimize FN) over precision
        // False negatives are worse than false positives because:
        // - FN: Tables are missed and processed incorrectly by Java path
        // - FP: Backend processes correctly, just slightly slower
        Assertions.assertTrue(recall >= 0.90, "Recall should be at least 90%, was: " + recall);
        // Precision threshold is lower because FP is acceptable (backend handles it)
        Assertions.assertTrue(precision >= 0.20, "Precision should be at least 20%, was: " + precision);
    }

    /**
     * Triage a single document and return the decision.
     * Returns BACKEND if any page is routed to BACKEND.
     */
    private TriageDecision triageDocument(File pdfFile) throws IOException {
        String pdfPath = pdfFile.getAbsolutePath();
        Config config = new Config();

        // Use DocumentProcessor.preprocessing to properly initialize
        DocumentProcessor.preprocessing(pdfPath, config);

        int numPages = StaticContainers.getDocument().getNumberOfPages();

        for (int pageNum = 0; pageNum < numPages; pageNum++) {
            // Filter page contents
            List<IObject> filteredContents = ContentFilterProcessor.getFilteredContents(
                pdfPath,
                StaticContainers.getDocument().getArtifacts(pageNum),
                pageNum,
                config
            );

            // Triage the page
            TriageResult result = TriageProcessor.classifyPage(
                filteredContents, pageNum, new HybridConfig()
            );

            // If any page is BACKEND, the whole document needs BACKEND
            if (result.getDecision() == TriageDecision.BACKEND) {
                return TriageDecision.BACKEND;
            }
        }

        return TriageDecision.JAVA;
    }

    @Test
    public void testSingleDocumentTriage() throws IOException {
        if (!benchmarkPdfsAvailable) {
            return;
        }

        // Test a known table document
        File tableDoc = BENCHMARK_PDF_DIR.resolve("01030000000045.pdf").toFile();
        if (tableDoc.exists()) {
            TriageDecision decision = triageDocument(tableDoc);
            // This document has a table, so it should ideally be BACKEND
            Assertions.assertEquals(TriageDecision.BACKEND, decision,
                "Document 01030000000045 has a table and should be routed to BACKEND");
        }

        // Test a known non-table document
        File nonTableDoc = BENCHMARK_PDF_DIR.resolve("01030000000001.pdf").toFile();
        if (nonTableDoc.exists()) {
            TriageDecision decision = triageDocument(nonTableDoc);
            Assertions.assertEquals(TriageDecision.JAVA, decision,
                "Document 01030000000001 has no table and should be routed to JAVA");
        }
    }
}
