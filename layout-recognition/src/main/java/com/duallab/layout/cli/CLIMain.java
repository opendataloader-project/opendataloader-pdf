package com.duallab.layout.cli;

import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.processors.DocumentProcessor;
import org.apache.commons.cli.*;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;

public class CLIMain {

    private static final String HELP = "[options] <INPUT FILE OR FOLDER> <OUTPUT FILE OR FOLDER>\n Options:";
    
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
        String password = null;
        if (commandLine.hasOption(CLIOptions.PASSWORD_OPTION)) {
            password = commandLine.getOptionValue(CLIOptions.PASSWORD_OPTION);
        }

        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticLayoutContainers.setFindHiddenText(commandLine.hasOption(CLIOptions.HIDDEN_TEXT_OPTION));
        StaticContainers.setTextFormatting(commandLine.hasOption(CLIOptions.FORMATTED_TEXT_OPTION));

        File file = new File(arguments[0]);
        if (file.isDirectory()) {
            for (File pdf : file.listFiles()) {
                DocumentProcessor.processFile(pdf.getAbsolutePath(), arguments[1] + File.separator + pdf.getName(), password);
            }
        } else if (file.isFile()) {
            DocumentProcessor.processFile(file.getAbsolutePath(), arguments[1], password);
        }
    }

}
