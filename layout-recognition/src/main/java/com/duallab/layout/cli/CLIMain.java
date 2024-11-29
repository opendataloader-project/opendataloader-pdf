/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.cli;

import com.duallab.layout.utils.Config;
import com.duallab.layout.processors.DocumentProcessor;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>\n Options:";
    
    public static void main(String[] args) throws IOException {
        if (!checkCurrentDate()) {
            return;
        }
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
            for (File pdf : file.listFiles()) {
                DocumentProcessor.processFile(pdf.getAbsolutePath(), config);
            }
        } else if (file.isFile()) {
            DocumentProcessor.processFile(file.getAbsolutePath(), config);
        } else {
            LOGGER.log(Level.WARNING, "File or folder " + file.getAbsolutePath() + " not found.");
        }
    }

    private static boolean checkCurrentDate() {
        Date currentDate = new Date();
        Date lockDate = new Date(2025, Calendar.MARCH, 1);
        if (currentDate.after(lockDate)) {
            LOGGER.log(Level.WARNING, "Trial version expired March 1, 2025");
            return false;
        }
        return true;
    }
}
