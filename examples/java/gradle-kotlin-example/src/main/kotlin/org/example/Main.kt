package org.example

import com.hancom.opendataloader.pdf.processors.DocumentProcessor
import com.hancom.opendataloader.pdf.utils.Config
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main() {
    // 이 예제는 opendataloader-pdf-core 라이브러리를 사용하여 PDF 파일을 처리하는 방법을 보여줍니다.
    // 프로젝트 루트에 있는 샘플 PDF 파일을 사용합니다.
    val pdfFilePath = Paths.get("..", "..", "..", "samples", "pdf", "2408.02509v1.pdf").toString()
    val pdfFile = File(pdfFilePath)

    if (!pdfFile.exists()) {
        System.err.println("PDF file does not exist: ${pdfFile.absolutePath}")
        exitProcess(1)
    }

    // 출력 디렉토리를 'build/output' 폴더로 설정합니다.
    val outputDir = File("build/output")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    try {
        println("Processing PDF file: ${pdfFile.absolutePath}")

        // 라이브러리 설정을 구성합니다.
        val config = Config().apply {
            outputFolder = outputDir.absolutePath
            isGenerateJSON = true
            isGenerateMarkdown = true
            isGeneratePDF = true
        }

        // DocumentProcessor를 사용하여 PDF 파일을 처리합니다.
        DocumentProcessor.processFile(pdfFile.absolutePath, config)

        println("Processing finished successfully.")
        println("Check the output files in: ${outputDir.absolutePath}")

    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
}