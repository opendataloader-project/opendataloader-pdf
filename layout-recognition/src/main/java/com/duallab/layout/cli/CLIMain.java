package com.duallab.layout.cli;

import com.duallab.layout.processors.Config;
import com.duallab.layout.processors.DocumentProcessor;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>\n Options:";
    
    public static void main(String[] args) throws IOException {
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

}
