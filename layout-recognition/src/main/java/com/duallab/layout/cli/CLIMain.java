package com.duallab.layout.cli;

import com.duallab.layout.processors.Config;
import com.duallab.layout.processors.DocumentProcessor;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;

public class CLIMain {

    private static final String HELP = "[options] <INPUT FILE OR FOLDER> <OUTPUT FOLDER>\n Options:";
    
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
        if (commandLine.getArgs().length < 2) {
            formatter.printHelp(HELP, options);
            return;
        }

        String[] arguments = commandLine.getArgs();
        Config config = Config.createConfigFromCommandLine(commandLine);
        File file = new File(arguments[0]);
        if (file.isDirectory()) {
            for (File pdf : file.listFiles()) {
                DocumentProcessor.processFile(pdf.getAbsolutePath(), arguments[1], config);
            }
        } else if (file.isFile()) {
            DocumentProcessor.processFile(file.getAbsolutePath(), arguments[1], config);
        }
    }

}
