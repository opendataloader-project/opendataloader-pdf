package org.example.maven;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        // This example shows how to process a PDF file using the opendataloader-pdf-core library.
        // It uses a sample PDF file located in the project root.
        // The PDF file path can be changed as needed.

        // Set the relative path to the input PDF file based on the project's root directory.
        // Since this class is run from the 'examples/java/maven-example' directory, we need to move up to the parent directory.
        String pdfFilePath = Paths.get("..", "..", "..", "samples", "pdf", "2408.02509v1.pdf").toString();
        File pdfFile = new File(pdfFilePath);

        if (!pdfFile.exists()) {
            System.err.println("PDF file does not exist: " + pdfFile.getAbsolutePath());
            System.exit(1);
        }

        // Set the output directory to the 'target' folder.
        File outputDir = new File("target");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            System.out.println("Processing PDF file: " + pdfFile.getAbsolutePath());

            // Configure the library settings.
            Config config = new Config();
            // Set the output folder to the 'target' directory.
            config.setOutputFolder(outputDir.getAbsolutePath());
            // Set to generate JSON, Markdown, and annotated PDF results.
            config.setGenerateJSON(true);
            config.setGenerateMarkdown(true);
            config.setGeneratePDF(true);

            // Process the PDF file using OpenDataLoaderPDF.
            OpenDataLoaderPDF.processFile(pdfFile.getAbsolutePath(), config);

            System.out.println("Processing finished successfully.");
            System.out.println("Check the output files in: " + outputDir.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
