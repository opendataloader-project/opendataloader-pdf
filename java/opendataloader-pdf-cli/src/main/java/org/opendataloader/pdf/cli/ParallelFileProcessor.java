/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes multiple PDF files in parallel using separate JVM processes.
 *
 * <p>Each file is processed in its own JVM to avoid thread-safety issues
 * with verapdf's StaticContainers (plain static fields, not ThreadLocal).
 * This provides true process-level isolation where each JVM has its own
 * copy of all static state.
 */
public class ParallelFileProcessor {

    private static final Logger LOGGER = Logger.getLogger(ParallelFileProcessor.class.getCanonicalName());

    private final int parallelism;
    private final String[] originalArgs;

    public ParallelFileProcessor(int parallelism, String[] originalArgs) {
        this.parallelism = parallelism;
        this.originalArgs = originalArgs;
    }

    /**
     * Processes files in parallel using separate JVM processes.
     *
     * @param files The list of PDF files to process.
     * @return The number of files that failed processing.
     */
    public int processFiles(List<File> files) {
        if (files.isEmpty()) {
            return 0;
        }

        LOGGER.log(Level.INFO, "Processing {0} files with parallelism={1}",
            new Object[]{files.size(), parallelism});

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<Future<ProcessResult>> futures = new ArrayList<>();

        for (File file : files) {
            futures.add(executor.submit(() -> processFileInSubprocess(file)));
        }

        executor.shutdown();

        int failCount = 0;
        for (int i = 0; i < futures.size(); i++) {
            File file = files.get(i);
            try {
                ProcessResult result = futures.get(i).get();
                if (result.exitCode != 0) {
                    failCount++;
                    LOGGER.log(Level.SEVERE, "[{0}] Failed with exit code {1}",
                        new Object[]{file.getName(), result.exitCode});
                } else {
                    LOGGER.log(Level.INFO, "[{0}] Completed successfully", file.getName());
                }
            } catch (Exception e) {
                failCount++;
                LOGGER.log(Level.SEVERE, "[{0}] Exception: {1}",
                    new Object[]{file.getName(), e.getMessage()});
            }
        }

        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while waiting for processes");
        }

        LOGGER.log(Level.INFO, "Parallel processing complete: {0}/{1} succeeded",
            new Object[]{files.size() - failCount, files.size()});

        return failCount;
    }

    private ProcessResult processFileInSubprocess(File file) {
        try {
            List<String> command = buildSubprocessCommand(file);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.log(Level.INFO, "[{0}] {1}", new Object[]{file.getName(), line});
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output.toString());

        } catch (Exception e) {
            return new ProcessResult(-1, e.getMessage());
        }
    }

    private List<String> buildSubprocessCommand(File file) {
        List<String> command = new ArrayList<>();

        String javaHome = System.getProperty("java.home");
        String javaExe = javaHome + File.separator + "bin" + File.separator + "java";
        command.add(javaExe);

        String jarPath = findJarPath();
        command.add("-jar");
        command.add(jarPath);

        // Copy CLI args except --parallel and input file arguments
        command.addAll(filterArgsForSubprocess());

        command.add(file.getAbsolutePath());

        return command;
    }

    private String findJarPath() {
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && classPath.endsWith(".jar")) {
            return classPath;
        }
        String command = System.getProperty("sun.java.command");
        if (command != null && command.contains(".jar")) {
            String[] parts = command.split("\\s+");
            for (String part : parts) {
                if (part.endsWith(".jar")) {
                    return part;
                }
            }
        }
        throw new IllegalStateException(
            "Cannot determine JAR path for subprocess. "
            + "--parallel requires running via 'java -jar'");
    }

    /**
     * Filters original CLI args: removes --parallel and its value,
     * removes positional arguments (input files/dirs).
     */
    List<String> filterArgsForSubprocess() {
        List<String> filtered = new ArrayList<>();
        boolean skipNext = false;

        for (int i = 0; i < originalArgs.length; i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }

            String arg = originalArgs[i];

            // Skip --parallel and its value
            if ("--parallel".equals(arg)) {
                skipNext = true;
                continue;
            }

            // Skip positional arguments (input files/dirs)
            if (!arg.startsWith("-")) {
                continue;
            }

            // Check if this is a known option with a value argument
            if (isOptionWithValue(arg) && i + 1 < originalArgs.length
                    && !originalArgs[i + 1].startsWith("-")) {
                filtered.add(arg);
                filtered.add(originalArgs[i + 1]);
                skipNext = true;
            } else {
                filtered.add(arg);
            }
        }

        return filtered;
    }

    private static boolean isOptionWithValue(String option) {
        return option.equals("-o") || option.equals("--output-dir")
            || option.equals("-p") || option.equals("--password")
            || option.equals("-f") || option.equals("--format")
            || option.equals("--content-safety-off")
            || option.equals("--replace-invalid-chars")
            || option.equals("--table-method")
            || option.equals("--reading-order")
            || option.equals("--markdown-page-separator")
            || option.equals("--text-page-separator")
            || option.equals("--html-page-separator")
            || option.equals("--image-output")
            || option.equals("--image-format")
            || option.equals("--image-dir")
            || option.equals("--pages")
            || option.equals("--hybrid")
            || option.equals("--hybrid-mode")
            || option.equals("--hybrid-url")
            || option.equals("--hybrid-timeout");
    }

    private static class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
