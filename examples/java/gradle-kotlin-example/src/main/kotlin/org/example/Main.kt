package org.example

import com.hancom.opendataloader.pdf.api.Config
import com.hancom.opendataloader.pdf.api.OpenDataLoaderPDF
import java.io.File
import java.io.IOException
import java.nio.file.Paths

fun main() {
    // This example shows how to process a PDF file using the opendataloader-pdf-core library.
    // It uses a sample PDF file located in the project root.
    // The PDF file path can be changed as needed.

    // Set the relative path to the input PDF file based on the project's root directory.
    val pdfFilePath = Paths.get("..", "..", "..", "samples", "pdf", "2408.02509v1.pdf").toString()
    val pdfFile = File(pdfFilePath)

    if (!pdfFile.exists()) {
        System.err.println("PDF file does not exist: " + pdfFile.absolutePath)
        System.exit(1)
    }

    // Set the output directory to the 'build' folder for Gradle.
    val outputDir = File("build")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    try {
        println("Processing PDF file: " + pdfFile.absolutePath)

        // Configure the library settings.
        val config = Config()
        // Set the output folder to the 'build' directory.
        config.outputFolder = outputDir.absolutePath
        // Set to generate JSON, Markdown, and annotated PDF results.
        config.setGenerateJSON(true)
        config.setGenerateMarkdown(true)
        config.setGeneratePDF(true)

        // Process the PDF file using OpenDataLoaderPDF.
        OpenDataLoaderPDF.processFile(pdfFile.absolutePath, config)

        println("Processing finished successfully.")
        println("Check the output files in: " + outputDir.absolutePath)

    } catch (e: IOException) {
        e.printStackTrace()
        System.exit(1)
    }
}
