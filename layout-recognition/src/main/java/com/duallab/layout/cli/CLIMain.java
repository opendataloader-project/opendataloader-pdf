package com.duallab.layout.cli;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;

import static com.duallab.layout.containers.StaticLayoutContainers.*;
import static com.duallab.layout.processors.DocumentProcessor.processFile;

public class CLIMain {

    private static final String HELP = "[options] <INPUT FILE OR FOLDER> <OUTPUT FILE OR FOLDER>\n Options:";
    
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine;
        try {
            commandLine = (new DefaultParser()).parse(options, args);
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
        if (commandLine.hasOption("p")) {
            password = commandLine.getOptionValue("p");
        }
        findHiddenText = commandLine.hasOption("ht");
//        String pdfName = "demo_test_file.pdf";
//        String pdfName = "Test wihtout structure.pdf";
//        String pdfName = "PDFUA-Reference-01_(Matterhorn-Protocol_1-02).pdf";
        File file = new File(arguments[0]);
        if (file.isDirectory()) {
            for (File pdf : file.listFiles()) {
                processFile(pdf.getAbsolutePath(), arguments[1] + File.separator + pdf.getName(), password);
            }
        } else if (file.isFile()) {
            processFile(file.getAbsolutePath(), arguments[1], password);
        }
    }

    private static Options defineOptions() {
        Options options = new Options();
        Option findHiddenText = new Option("ht", "findhiddentext", false, "Find hidden text");
        findHiddenText.setRequired(false);
        options.addOption(findHiddenText);
        Option password = new Option("p", "password", true, "Specifies password");
        password.setRequired(false);
        options.addOption(password);
        return options;
    }
}