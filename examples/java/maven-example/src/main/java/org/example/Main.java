package org.example;

import com.hancom.opendataloader.pdf.processors.DocumentProcessor;
import com.hancom.opendataloader.pdf.utils.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        // 이 예제는 opendataloader-pdf-core 라이브러리를 사용하여 PDF 파일을 처리하는 방법을 보여줍니다.
        // 프로젝트 루트에 있는 샘플 PDF 파일을 사용합니다.
        // PDF 파일 경로는 필요에 따라 변경할 수 있습니다.

        // 프로젝트의 루트 디렉토리를 기준으로 입력 PDF 파일의 상대 경로를 설정합니다.
        // 이 클래스는 'examples/java/maven-example' 디렉토리에서 실행되므로, 상위 디렉토리로 이동해야 합니다.
        String pdfFilePath = Paths.get("..", "..", "..", "samples", "pdf", "2408.02509v1.pdf").toString();
        File pdfFile = new File(pdfFilePath);

        if (!pdfFile.exists()) {
            System.err.println("PDF file does not exist: " + pdfFile.getAbsolutePath());
            System.exit(1);
        }

        // 출력 디렉토리를 'target' 폴더로 설정합니다.
        File outputDir = new File("target");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            System.out.println("Processing PDF file: " + pdfFile.getAbsolutePath());

            // 라이브러리 설정을 구성합니다.
            Config config = new Config();
            // 출력 폴더를 'target' 디렉토리로 설정합니다.
            config.setOutputFolder(outputDir.getAbsolutePath());
            // JSON, 마크다운, 주석 처리된 PDF 결과물을 생성하도록 설정합니다.
            config.setGenerateJSON(true);
            config.setGenerateMarkdown(true);
            config.setGeneratePDF(true);

            // DocumentProcessor를 사용하여 PDF 파일을 처리합니다.
            DocumentProcessor.processFile(pdfFile.getAbsolutePath(), config);

            System.out.println("Processing finished successfully.");
            System.out.println("Check the output files in: " + outputDir.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
