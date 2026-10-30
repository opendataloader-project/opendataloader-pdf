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

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.cli.CLIOptions;
import org.opendataloader.pdf.hybrid.DoclingFastServerClient;
import org.opendataloader.pdf.hybrid.HybridConfig;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #644: the availability check of the hybrid backend waits 3 s, and no option changes it. A backend on a
 * serverless platform answers its first request only after a cold start, so the conversion stops with
 * "Hybrid server is not available".
 *
 * <p>Each case starts a mock backend whose health check answers after {@link #COLD_START_SECONDS} s, as such a
 * backend after a cold start. The option {@code --hybrid-availability-timeout} sets how long the check waits.
 */
class Issue644HybridAvailabilityTimeoutTest {

    private static final long COLD_START_SECONDS = 4;

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("").toString().replaceFirst("/$", "");
        server.enqueue(new MockResponse.Builder().code(200).body("ok")
            .headersDelay(COLD_START_SECONDS, TimeUnit.SECONDS).build());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** The Java API: an availability timeout of 10 s lets the check wait for the cold start. */
    @Test
    void checkWaitsForTheAvailabilityTimeoutOfTheConfig() throws Exception {
        HybridConfig config = new HybridConfig();
        config.setUrl(baseUrl);
        try {
            Method setter = HybridConfig.class.getMethod("setAvailabilityTimeoutMs", int.class);
            setter.invoke(config, 10_000);
        } catch (NoSuchMethodException e) {
            // Without the setting, the check below waits 3 s and stops with the error of the issue.
        }
        DoclingFastServerClient client = new DoclingFastServerClient(config);
        try {
            client.checkAvailability();
        } finally {
            client.shutdown();
        }
    }

    /** The CLI: {@code --hybrid-availability-timeout 10000} lets the check wait for the cold start. */
    @Test
    void checkWaitsForTheAvailabilityTimeoutOfTheCli() throws Exception {
        Config config = configFrom("--hybrid", "docling-fast", "--hybrid-url", baseUrl,
            "--hybrid-availability-timeout", "10000");
        DoclingFastServerClient client = new DoclingFastServerClient(config.getHybridConfig());
        try {
            assertDoesNotThrow(client::checkAvailability);
        } finally {
            client.shutdown();
        }
    }

    /** A value that is not a positive number of milliseconds is refused, and the message names the option. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "abc"})
    void invalidAvailabilityTimeoutIsRefused(String value) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> configFrom("--hybrid", "docling-fast", "--hybrid-availability-timeout", value));
        assertTrue(exception.getMessage().contains("--hybrid-availability-timeout"), exception.getMessage());
    }

    /** Without the option, the check waits 3 s, as before: a backend that answers after 4 s is not available. */
    @Test
    void checkWithoutTheOptionStopsAfterThreeSeconds() throws Exception {
        Config config = configFrom("--hybrid", "docling-fast", "--hybrid-url", baseUrl);
        DoclingFastServerClient client = new DoclingFastServerClient(config.getHybridConfig());
        try {
            IOException exception = assertThrows(IOException.class, client::checkAvailability);
            assertTrue(exception.getMessage().contains("Hybrid server is not available"), exception.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private Config configFrom(String... options) throws Exception {
        Path pdf = Files.createFile(tempDir.resolve("input-" + System.nanoTime() + ".pdf"));
        String[] args = new String[options.length + 1];
        System.arraycopy(options, 0, args, 0, options.length);
        args[options.length] = pdf.toString();
        CommandLine commandLine = new DefaultParser().parse(CLIOptions.defineOptions(), args);
        return CLIOptions.createConfigFromCommandLine(commandLine);
    }
}
