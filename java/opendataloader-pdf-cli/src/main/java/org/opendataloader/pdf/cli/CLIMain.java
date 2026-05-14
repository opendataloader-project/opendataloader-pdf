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
package org.opendataloader.pdf.cli;

import org.apache.commons.cli.*;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.api.cli.CLIOptions;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.exceptions.InvalidPdfFileException;
import org.verapdf.exceptions.InvalidPasswordException;

import java.io.File;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>...\n Options:";

    private enum InputSource { CLI_ARGUMENT, DIRECTORY_CHILD }

    /**
     * Result of processing a path: whether all files succeeded, and how many
     * PDF files were processed under it (counted recursively for directories,
     * 1 or 0 for a single file). Used by {@link #processDirectory} to print a
     * clear summary when a user-supplied folder contains no PDFs (PDFDLOSP-15).
     */
    private static final class PathResult {
        final boolean allSucceeded;
        final int pdfCount;

        PathResult(boolean allSucceeded, int pdfCount) {
            this.allSucceeded = allSucceeded;
            this.pdfCount = pdfCount;
        }
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the CLI with the given arguments and returns the exit code.
     *
     * @param args command-line arguments
     * @return 0 on success, non-zero on failure
     */
    static int run(String[] args) {
        Options options = CLIOptions.defineOptions();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(HELP, options);
            return 2;
        }

        // Handle --export-options before requiring input files
        if (commandLine.hasOption(CLIOptions.EXPORT_OPTIONS_LONG_OPTION)) {
            CLIOptions.exportOptionsAsJson(System.out);
            return 0;
        }

        if (commandLine.getArgs().length < 1) {
            formatter.printHelp(HELP, options);
            return 0;
        }

        String[] arguments = commandLine.getArgs();

        Config config;
        boolean quiet;
        try {
            config = CLIOptions.createConfigFromCommandLine(commandLine);
            quiet = commandLine.hasOption(CLIOptions.QUIET_OPTION) || commandLine.hasOption("quiet");
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
            formatter.printHelp(HELP, options);
            return 2;
        }
        configureLogging(quiet);
        boolean hasFailure = false;
        try {
            for (String argument : arguments) {
                if (!processPath(new File(argument), config, InputSource.CLI_ARGUMENT).allSucceeded) {
                    hasFailure = true;
                }
            }
        } finally {
            // Release resources (e.g., hybrid client thread pools)
            OpenDataLoaderPDF.shutdown();
        }
        return hasFailure ? 1 : 0;
    }

    private static void configureLogging(boolean quiet) {
        if (!quiet) {
            return;
        }
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.OFF);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.OFF);
        }
        LOGGER.setLevel(Level.OFF);
    }

    /**
     * Processes a file or directory, returning true if all files succeeded.
     *
     * <p>{@code source} distinguishes user-provided arguments
     * ({@link InputSource#CLI_ARGUMENT}) from files discovered during directory
     * traversal ({@link InputSource#DIRECTORY_CHILD}): a non-PDF given directly
     * on the command line is reported as an error, while non-PDF files inside a
     * directory are silently skipped (preserves batch-folder processing).
     */
    private static PathResult processPath(File file, Config config, InputSource source) {
        if (!file.exists()) {
            LOGGER.log(Level.WARNING, "File or folder " + file.getAbsolutePath() + " not found.");
            return new PathResult(false, 0);
        }
        if (file.isDirectory()) {
            return processDirectory(file, config, source);
        }
        if (file.isFile()) {
            boolean isPdf = isPdfFile(file);
            if (source == InputSource.CLI_ARGUMENT && !isPdf) {
                System.out.println("Error: '" + file.getName()
                    + "' is not a PDF file. Input must be a PDF file or a folder containing PDF files.");
                return new PathResult(false, 0);
            }
            return new PathResult(processFile(file, config, source), isPdf ? 1 : 0);
        }
        return new PathResult(true, 0);
    }

    /**
     * Counts PDF files processed under the directory rooted at {@code file}
     * (recursively), so the CLI can surface a clear summary when a
     * user-supplied folder contains no PDFs. Without this feedback the program
     * would exit silently with status 0 and the user could not distinguish
     * "wrong folder", "empty folder", and "successful run" (PDFDLOSP-15).
     *
     * <p>The summary is only printed for folders given directly on the command
     * line ({@link InputSource#CLI_ARGUMENT}) — nested subdirectories aggregate
     * upward into the top-level count rather than each printing their own line.
     *
     * <p>The summary line is the final <em>result</em> of the run, not a log
     * entry, and is therefore intentionally emitted on stdout even under
     * {@code --quiet}. {@code --quiet} suppresses processing logs; users
     * still need to see whether anything was actually processed. The path
     * shown is {@link File#getPath()} (the literal argument the user typed,
     * e.g. {@code .} or {@code basic_images}) rather than {@link File#getName()},
     * which would be empty for {@code .} or trailing-slash inputs.
     */
    private static PathResult processDirectory(File file, Config config, InputSource source) {
        File[] children = file.listFiles();
        if (children == null) {
            LOGGER.log(Level.WARNING, "Unable to read folder " + file.getAbsolutePath());
            return new PathResult(false, 0);
        }
        boolean allSucceeded = true;
        int pdfCount = 0;
        for (File child : children) {
            PathResult childResult = processPath(child, config, InputSource.DIRECTORY_CHILD);
            if (!childResult.allSucceeded) {
                allSucceeded = false;
            }
            pdfCount += childResult.pdfCount;
        }
        if (source == InputSource.CLI_ARGUMENT) {
            if (pdfCount == 0) {
                System.out.println("No PDF files found in '" + file.getPath() + "'.");
            } else {
                System.out.println("Processed " + pdfCount + " PDF file"
                    + (pdfCount == 1 ? "" : "s") + " in '" + file.getPath() + "'.");
            }
        }
        return new PathResult(allSucceeded, pdfCount);
    }

    /**
     * Processes a single PDF file.
     *
     * <p>{@code source} controls how an {@link InvalidPdfFileException} from
     * the magic-number guard is surfaced: {@link InputSource#CLI_ARGUMENT}
     * routes it to stdout as a user-facing error and fails the run;
     * {@link InputSource#DIRECTORY_CHILD} logs a WARNING and treats the file
     * as silently skipped so batch-folder runs can still exit 0.
     *
     * @param file the file to process
     * @param config the processing configuration
     * @param source whether the file came from a CLI argument or directory traversal
     * @return true if processing succeeded (or the file was a silently
     *         skipped non-PDF inside a directory), false on error.
     */
    private static boolean processFile(File file, Config config, InputSource source) {
        if (!isPdfFile(file)) {
            LOGGER.log(Level.FINE, "Skipping non-PDF file " + file.getAbsolutePath());
            return true;
        }
        try {
            OpenDataLoaderPDF.processFile(file.getAbsolutePath(), config);
            return true;
        } catch (InvalidPdfFileException invalid) {
            if (source == InputSource.CLI_ARGUMENT) {
                System.out.println("Error: " + invalid.getMessage());
                return false;
            }
            LOGGER.log(Level.WARNING, invalid.getMessage() + " Skipping.");
            return true;
        } catch (InvalidPasswordException exception) {
            String password = config.getPassword();
            String message = (password == null || password.isEmpty())
                ? "Error: '" + file.getName() + "' is password-protected. Use --password option."
                : "Error: Incorrect password for '" + file.getName() + "'.";
            System.out.println(message);
            return false;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Exception during processing file " + file.getAbsolutePath() + ": " +
                exception.getMessage(), exception);
            return false;
        } finally {
            StaticLayoutContainers.closeContrastRatioConsumer();
        }
    }

    private static boolean isPdfFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName();
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
