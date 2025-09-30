/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.cli;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.apache.commons.cli.*;

import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>...\n Options:";

    public static void main(String[] args) {
        Options options = CLIOptions.defineOptions();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(HELP, options);
            return;
        }
        if (commandLine.getArgs().length < 1) {
            formatter.printHelp(HELP, options);
            return;
        }

        String[] arguments = commandLine.getArgs();
        Config config;
        try {
            config = CLIOptions.createConfigFromCommandLine(commandLine);
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
            formatter.printHelp(HELP, options);
            return;
        }
        for (String argument : arguments) {
            processPath(new File(argument), config);
        }
    }

    private static void processPath(File file, Config config) {
        if (!file.exists()) {
            LOGGER.log(Level.WARNING, "File or folder " + file.getAbsolutePath() + " not found.");
            return;
        }
        if (file.isDirectory()) {
            processDirectory(file, config);
        } else if (file.isFile()) {
            processFile(file, config);
        }
    }

    private static void processDirectory(File file, Config config) {
        File[] children = file.listFiles();
        if (children == null) {
            LOGGER.log(Level.WARNING, "Unable to read folder " + file.getAbsolutePath());
            return;
        }
        for (File child : children) {
            processPath(child, config);
        }
    }

    private static void processFile(File file, Config config) {
        if (!isPdfFile(file)) {
            LOGGER.log(Level.FINE, "Skipping non-PDF file " + file.getAbsolutePath());
            return;
        }
        try {
            OpenDataLoaderPDF.processFile(file.getAbsolutePath(), config);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Exception during processing file " + file.getAbsolutePath() + ": " +
                exception.getMessage(), exception);
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
