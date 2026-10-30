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
package org.opendataloader.pdf.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.apache.commons.cli.DefaultParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.api.cli.CLIOptions;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #741: in hybrid mode, the CLI writes no file with the triage decision of each page. The code that writes it
 * exists, but the CLI gives it no folder, so a user who scores the routing must parse the debug log.
 *
 * <p>Each case converts text-only PDFs with {@code --hybrid docling-fast} against a mock backend that answers the
 * health check, and that answers each conversion with an empty document. The option
 * {@code --hybrid-triage-log} writes {@code triage/<name>.json} in the output folder. The samples and the
 * outputs stay in {@code target/test-samples/}, in the folders whose names start with {@code issue-741}.
 */
class Issue741HybridTriageLogTest {

    private static final String EMPTY_DOCUMENT = "{\"status\": \"success\","
        + "\"document\": {\"json_content\": {\"texts\": [], \"pages\": {}}},"
        + "\"processing_time\": 0.1, \"errors\": [], \"failed_pages\": []}";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory caches clients per backend name, so a client wired
        // to another test's URL would otherwise receive our requests.
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/health".equals(request.getTarget())) {
                    return new MockResponse.Builder().code(200).body("ok").build();
                }
                return new MockResponse.Builder().body(EMPTY_DOCUMENT)
                    .addHeader("Content-Type", "application/json").build();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            server.close();
        } finally {
            OpenDataLoaderPDF.shutdown();
        }
    }

    /** The Java API: with the triage log on, the decision of each page is in {@code triage/<name>.json}. */
    @Test
    void triageLogOfTheConfigIsWrittenNextToTheOutputs() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-741-api");
        Path pdf = directory.resolve("plan.pdf");
        TestSamples.writeTextPdf(pdf, 2, "The plan of the site office has three parts.");
        Config config = hybridConfig(directory);
        try {
            Method setter = HybridConfig.class.getMethod("setWriteTriageLog", boolean.class);
            setter.invoke(config.getHybridConfig(), true);
        } catch (NoSuchMethodException e) {
            // Without the setting, the conversion below writes no triage log, as in the issue.
        }
        DocumentProcessor.processFile(pdf.toString(), config);

        assertTriageLog(directory.resolve("triage").resolve("plan.json"), "plan.pdf", 2);
    }

    /** The CLI: {@code --hybrid-triage-log} writes the triage log in the output folder. */
    @Test
    void triageLogOfTheCliIsWrittenNextToTheOutputs() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-741-cli");
        Path pdf = directory.resolve("plan.pdf");
        TestSamples.writeTextPdf(pdf, 1, "The plan of the site office has three parts.");
        Config config = CLIOptions.createConfigFromCommandLine(new DefaultParser().parse(CLIOptions.defineOptions(),
            new String[]{"--hybrid", "docling-fast", "--hybrid-url", serverUrl(), "--hybrid-triage-log",
                "-o", directory.toString(), "-f", "json", pdf.toString()}));
        DocumentProcessor.processFile(pdf.toString(), config);

        assertTriageLog(directory.resolve("triage").resolve("plan.json"), "plan.pdf", 1);
    }

    /** Two documents converted to one folder each have their own triage log: neither log replaces the other. */
    @Test
    void eachDocumentHasItsOwnTriageLog() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-741-two");
        Path first = directory.resolve("first.pdf");
        Path second = directory.resolve("second.pdf");
        TestSamples.writeTextPdf(first, 1, "The first plan of the site office.");
        TestSamples.writeTextPdf(second, 3, "The second plan of the site office.");
        for (Path pdf : new Path[]{first, second}) {
            Config config = CLIOptions.createConfigFromCommandLine(new DefaultParser().parse(CLIOptions.defineOptions(),
                new String[]{"--hybrid", "docling-fast", "--hybrid-url", serverUrl(), "--hybrid-triage-log",
                    "-o", directory.toString(), "-f", "json", pdf.toString()}));
            DocumentProcessor.processFile(pdf.toString(), config);
        }

        assertTriageLog(directory.resolve("triage").resolve("first.json"), "first.pdf", 1);
        assertTriageLog(directory.resolve("triage").resolve("second.json"), "second.pdf", 3);
    }

    /**
     * The triage log does not replace an output of another document: {@code foo_triage.json} is the JSON output of
     * {@code foo_triage.pdf}, and the log of {@code foo.pdf} goes to {@code triage/foo.json}.
     */
    @Test
    void triageLogDoesNotReplaceTheOutputOfAnotherDocument() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-741-names");
        Path plain = directory.resolve("foo.pdf");
        Path named = directory.resolve("foo_triage.pdf");
        TestSamples.writeTextPdf(named, 1, "The document whose name ends with triage.");
        TestSamples.writeTextPdf(plain, 1, "The plan of the site office has three parts.");
        for (Path pdf : new Path[]{named, plain}) {
            Config config = CLIOptions.createConfigFromCommandLine(new DefaultParser().parse(CLIOptions.defineOptions(),
                new String[]{"--hybrid", "docling-fast", "--hybrid-url", serverUrl(), "--hybrid-triage-log",
                    "-o", directory.toString(), "-f", "json", pdf.toString()}));
            DocumentProcessor.processFile(pdf.toString(), config);
        }

        JsonNode output = new ObjectMapper().readTree(directory.resolve("foo_triage.json").toFile());
        assertEquals("foo_triage.pdf", output.get("file name").asText(), "the JSON output of foo_triage.pdf");
        assertTriageLog(directory.resolve("triage").resolve("foo.json"), "foo.pdf", 1);
        assertTriageLog(directory.resolve("triage").resolve("foo_triage.json"), "foo_triage.pdf", 1);
    }

    /** Without the option, the output folder has no triage log, as before. */
    @Test
    void noTriageLogWithoutTheOption() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-741-off");
        Path pdf = directory.resolve("plan.pdf");
        TestSamples.writeTextPdf(pdf, 1, "The plan of the site office has three parts.");
        DocumentProcessor.processFile(pdf.toString(), hybridConfig(directory));

        assertTrue(Files.exists(directory.resolve("plan.json")), "the conversion wrote its output");
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().contains("triage")),
                "a triage log without the option");
        }
    }

    private Config hybridConfig(Path directory) {
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setUrl(serverUrl());
        return config;
    }

    private String serverUrl() {
        return server.url("").toString().replaceAll("/$", "");
    }

    /** The log names the document, and has one decision of the Java path or the backend for each page. */
    private static void assertTriageLog(Path log, String document, int pages) throws IOException {
        assertTrue(Files.exists(log), "no triage log: triage/" + log.getFileName());
        JsonNode root = new ObjectMapper().readTree(log.toFile());
        assertEquals(document, root.get("document").asText());
        assertEquals("docling-fast", root.get("hybrid").asText());
        assertEquals(pages, root.get("triage").size());
        for (JsonNode page : root.get("triage")) {
            assertTrue(page.get("decision").asText().matches("JAVA|BACKEND"), page.toString());
            assertTrue(page.has("confidence") && page.has("signals"), page.toString());
        }
    }
}
