/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import com.hancom.opendataloader.pdf.utils.ResourceLoader;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LinesCollection;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;
import net.sourceforge.tess4j.*;
import org.verapdf.tools.StaticResources;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.*;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OCRProcessor {
    
    private static Tesseract tesseract = null;
    private static File tessDataFolder = null;

    public static void process(String pdfName, String password, List<List<IObject>> contents, String ocrLanguages) throws IOException {
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(pdfName, password, true, 2000f)) {
            for (int pageNumber = 0; pageNumber < StaticResources.getDocument().getNumberOfPages(); pageNumber++) {
                List<IObject> pageContents = contents.get(pageNumber);
                pageContents.clear();
//                if (!isPossibleScannedPage(pageContents)) {
//                    continue;
//                }
                BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
                BufferedImage image = contrastRatioConsumer.getRenderPage(pageNumber);
                double dpiScaling = contrastRatioConsumer.getDpiScalingForPage(pageNumber);
                detectLines(image, pageBoundingBox, dpiScaling);
                List<Word> words = processTesseract(image, ocrLanguages);
                for (Word word : words) {
                    pageContents.add(createTextChunkFromWord(word, pageBoundingBox, dpiScaling));
                }
            }
        } catch (TesseractException e) {
            throw new RuntimeException(e);
        }
        StaticContainers.setLinesCollection(new LinesCollection());
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }
    
    private static TextChunk createTextChunkFromWord(Word word, BoundingBox pageBoundingBox, double dpiScaling) {
        TextChunk textChunk = new TextChunk(word.getText());
        textChunk.setBoundingBox(transformBBoxFromImageToPDFCoordinates(new BoundingBox(word.getBoundingBox().getMinX(), 
                word.getBoundingBox().getMinY(), word.getBoundingBox().getMaxX(), word.getBoundingBox().getMaxY()), 
                pageBoundingBox, dpiScaling));
        textChunk.getBoundingBox().setPageNumber(pageBoundingBox.getPageNumber());
        textChunk.setBaseLine(textChunk.getBoundingBox().getBottomY());
        textChunk.setFontColor(new double[]{0.0});
        textChunk.setFontSize(textChunk.getBoundingBox().getHeight());
        textChunk.setItalicAngle(0);
        List<Double> symbolEnds = new ArrayList<>();
        symbolEnds.add(textChunk.getLeftX());
        symbolEnds.add(textChunk.getRightX());
        textChunk.setSymbolEnds(symbolEnds);
        return textChunk;
    }
    
    private static void createTesseract(String ocrLanguages) {
        tesseract = new Tesseract();
        tessDataFolder = ResourceLoader.loadResource("tessdata");
        tesseract.setDatapath(tessDataFolder.getAbsolutePath());
        tesseract.setLanguage(ocrLanguages);//"eng+kor"
        tesseract.setVariable("wordrec_max_space_width", "15");
    }

    private static List<Word> processTesseract(BufferedImage image, String ocrLanguages) throws TesseractException, IOException {
        if (tesseract == null) {
            createTesseract(ocrLanguages);
        }
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_SYMBOL);
        return words;
    }
    
    private static boolean isPossibleScannedPage(List<IObject> pageContents) {
        boolean isContainsImage = false;
        for (IObject content : pageContents) {
            if (content instanceof TextChunk) {
                return false;
            }
            if (content instanceof ImageChunk) {
                isContainsImage = true;
            }
        }
        return isContainsImage;
    }

    public static BoundingBox transformBBoxFromImageToPDFCoordinates(BoundingBox boundingBox,
                                                                     BoundingBox pageBoundingBox, double dpiScaling) {
        return new BoundingBox(boundingBox.getPageNumber(), boundingBox.getLeftX() / dpiScaling,
                pageBoundingBox.getHeight() - boundingBox.getTopY() / dpiScaling,
                boundingBox.getRightX() / dpiScaling,
                pageBoundingBox.getHeight() - boundingBox.getBottomY() / dpiScaling);
    }

    private static void detectLines(BufferedImage bufferedImage, BoundingBox pageBoundingBox, double dpiScaling) {
        Mat image = Java2DFrameUtils.toMat(bufferedImage);
        StaticContainers.getDocument().getArtifacts(pageBoundingBox.getPageNumber()).clear();
        List<double[]> lines = detectLines(image);
        for (double[] line : lines) {
            LineChunk lineChunk = new LineChunk(pageBoundingBox.getPageNumber(), 
                    line[0] / dpiScaling, 
                    pageBoundingBox.getHeight() - line[1] / dpiScaling, 
                    line[2] / dpiScaling, 
                    pageBoundingBox.getHeight() - line[3] / dpiScaling);
            StaticContainers.getDocument().getArtifacts(pageBoundingBox.getPageNumber()).add(lineChunk);
        }
    }

    private static List<double[]> detectLines(Mat image) {
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(image, gray, opencv_imgproc.COLOR_BGR2GRAY);
        Mat enhanced = new Mat();
        opencv_imgproc.equalizeHist(gray, enhanced);
        Mat edges = new Mat();
        opencv_imgproc.Canny(enhanced, edges, 50, 150);
        Vec4iVector lines = new Vec4iVector();
        opencv_imgproc.HoughLinesP(edges, lines, 1, Math.PI/180, 50, 30, 10);
        List<double[]> detectedLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Scalar4i lineVec = lines.get(i);
            double[] lineData = {
                    lineVec.get(0), // x1
                    lineVec.get(1), // y1
                    lineVec.get(2), // x2
                    lineVec.get(3)  // y2
            };
            detectedLines.add(lineData);
        }
        return detectedLines;
    }
}
