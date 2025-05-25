/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.cli;

import com.hancom.opendataloader.pdf.processors.DocumentProcessor;
import com.hancom.opendataloader.pdf.utils.Config;
import org.apache.commons.cli.*;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>\n Options:";

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
        Config config = Config.createConfigFromCommandLine(commandLine);
        File file = new File(arguments[0]);
        if (file.isDirectory()) {
            processDirectory(file, config);
        } else if (file.isFile()) {
            processFile(file, config);
        } else {
            LOGGER.log(Level.WARNING, "File or folder " + file.getAbsolutePath() + " not found.");
        }
    }

    private static void processDirectory(File file, Config config) {
        for (File pdf : file.listFiles()) {
            if (pdf.isDirectory()) {
                processDirectory(pdf, config);
            } else {
                processFile(pdf, config);
            }
        }
    }

    private static void processFile(File file, Config config) {
        try {
            if (DocumentProcessor.generateTableResults) {
                DocumentProcessor.generateTableResults(file, config);
            } else {
                DocumentProcessor.processFile(file.getAbsolutePath(), config);
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Exception during processing file " + file.getAbsolutePath() + ": " +
                    exception.getMessage(), exception);
        }
    }
}
