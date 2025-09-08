package org.example

import com.hancom.opendataloader.pdf.api.Config
import com.hancom.opendataloader.pdf.api.OpenDataLoaderPDF
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main() {
    // This example shows how to process a PDF file using the opendataloader-pdf-core library.
    // It uses a sample PDF file located in the project root.
    val pdfFilePath = Paths.get("..", "..", "..", "samples", "pdf", "2408.02509v1.pdf").toString()
    val pdfFile = File(pdfFilePath)

    if (!pdfFile.exists()) {
        System.err.println("PDF file does not exist: ${pdfFile.absolutePath}")
        exitProcess(1)
    }

    // Set the output directory to the 'build/output' folder.
    val outputDir = File("build/output")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    try {
        println("Processing PDF file: ${pdfFile.absolutePath}")

        // Configure the library settings.
        val config = Config().apply {
            outputFolder = outputDir.absolutePath
            setGenerateJSON(true)
            setGenerateMarkdown(true)
            setGeneratePDF(true)
        }

        // Process the PDF file using OpenDataLoaderPDF.
        OpenDataLoaderPDF.processFile(pdfFile.absolutePath, config)

        println("Processing finished successfully.")
        println("Check the output files in: ${outputDir.absolutePath}")

    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
}