/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import com.hancom.opendataloader.pdf.containers.StaticLayoutContainers;
import com.hancom.opendataloader.pdf.utils.table_transformer.TableBorderJsonBuilder;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TableTransformerProcessor {

    private static final Logger LOGGER = Logger.getLogger(TableTransformerProcessor.class.getCanonicalName());

    // This options causes TATR to generate cell bboxes based on provided words instead of rows and columns
    // It will cause cell bboxes to be a lot smaller than in Java
    // We need to decide if we want to use words generation or not
    private static final boolean TATR_USES_WORDS = false;

    public static List<List<TableBorder>> processTableTransformer(String pdfName, String password, File scriptFolder,
            String pythonPath, File resultFolder, List<List<IObject>> contents) throws IOException {
        resultFolder.mkdir();
        String fileName = new File(pdfName).getName();
        File fileResultFolder = new File(resultFolder + File.separator + fileName.substring(0, fileName.length() - 4));
        fileResultFolder.mkdir();
        File imagesFolder = new File(fileResultFolder + File.separator + "images_folder");
        imagesFolder.mkdir();
        File wordsFolder = new File(fileResultFolder + File.separator + "words_folder");
        if (TATR_USES_WORDS) {
            wordsFolder.mkdir();
        }
        File tableTransformerResultFolder = new File(fileResultFolder + File.separator + "result_folder");
        resultFolder.mkdir();
        prepareInputForTableTransformerPython(pdfName, password, contents, imagesFolder, wordsFolder);
        try {
            runTableTransformerPython(scriptFolder, pythonPath, imagesFolder, wordsFolder, tableTransformerResultFolder);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return parseTableTransformerJSONs(tableTransformerResultFolder);
    }

    private static void runTableTransformerPython(File scriptFolder, String executable, File imagesFolder, File wordsFolder,
            File resultFolder) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(
                executable, "inference.py",
                "--mode", "extract",
                "--detection_device", "cpu",
                "--structure_device", "cpu",
                "--image_dir", imagesFolder.getAbsolutePath(),
                "--out_dir", resultFolder.getAbsolutePath(),
                "-lo");
        if (TATR_USES_WORDS) {
            builder.command().add("--words_dir");
            builder.command().add(wordsFolder.getAbsolutePath());
        }
        builder.directory(scriptFolder);
        builder.redirectOutput(new File("python_output.txt"));
        builder.redirectError(new File("python_error.txt"));
        Process process = builder.start();
        process.waitFor();
    }
    
    // This is the original parsing method designed for _objects.json isntead of _cells.json
    /*
    private static List<IObject> parseTableTransformerJson(File jsonFile, int pageNumber, double dpiScaling) {
        List<IObject> pageContents = new ArrayList<>();
        SortedSet<BoundingBox> tableRowsBBoxes = new TreeSet<>(Comparator.comparing(BoundingBox::getTopY).reversed());
        SortedSet<BoundingBox> tableColumnsBBoxes = new TreeSet<>(Comparator.comparing(BoundingBox::getLeftX));
        List<BoundingBox> tableSpanningCellsBBoxes = new ArrayList<>();
        try {
            DocumentContext documentContext = JsonPath.using(Configuration.defaultConfiguration()).parse(jsonFile);
            Map<String, Object> jsonMap = documentContext.json();
            JSONArray jsonArray = (JSONArray)jsonMap.get("objects");
            for (Object o : jsonArray) {
                Map<String, Object> map = (Map<String, Object>) o;
                JSONArray bbox = (JSONArray) map.get("bbox");
                String label = (String) map.get("label");
                BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
                BoundingBox boundingBox = new BoundingBox(pageNumber, (double) bbox.get(0), (double) bbox.get(1), 
                        (double) bbox.get(2), (double) bbox.get(3));
                if ("table row".equals(label)) {
                    tableRowsBBoxes.add(transformBBoxFromImageToPDFCoordinates(boundingBox, pageBoundingBox, dpiScaling));
                } else if ("table column".equals(label)) {
                    tableColumnsBBoxes.add(transformBBoxFromImageToPDFCoordinates(boundingBox, pageBoundingBox, dpiScaling));
                } else if ("table spanning cell".equals(label)) {
                    tableSpanningCellsBBoxes.add(transformBBoxFromImageToPDFCoordinates(boundingBox, pageBoundingBox, dpiScaling));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,"Exception during table transformer json parsing");
        }
        if (!tableColumnsBBoxes.isEmpty() && !tableRowsBBoxes.isEmpty()) {
//            create TableBorder
//            TableBorder border = new TableBorder(pageNumber, tableRowsBBoxes, tableColumnsBBoxes, tableSpanningCellsBBoxes);
//            pageContents.add(border);
        }
        return pageContents;
    }
     */

    private static List<TableBorder> parseTableTransformerJson(File jsonFile, int pageNumber, double dpiScaling) {
        List<TableBorder> pageContents = new ArrayList<>();
        try {
            BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
            TableBorderJsonBuilder builder = new TableBorderJsonBuilder(jsonFile, pageBoundingBox, dpiScaling, pageNumber);
            TableBorder border = builder.build();

            if (border == null) {
                LOGGER.log(Level.WARNING, "Failed to build table object from JSON representation");
            } else {
                if (border.getNumberOfColumns() > 1 && border.getNumberOfRows() > 1) {
                    pageContents.add(border);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception during table transformer json parsing on page " + (pageNumber + 1));
        }
        return pageContents;
    }

    private static List<List<TableBorder>> parseTableTransformerJSONs(File resultFolder) {
        List<List<TableBorder>> detectedContents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticResources.getDocument().getNumberOfPages(); pageNumber++) {
            List<TableBorder> pageContents = new ArrayList<>();
            int jsonIndex = 0;
            while (true) {
                File jsonFile = new File(resultFolder + File.separator + "image" + pageNumber + "_" + jsonIndex + "_cells.json");
                if (jsonFile.exists()) {
                    pageContents.addAll(parseTableTransformerJson(jsonFile, pageNumber,
                            StaticLayoutContainers.getContrastRatioConsumer().getDpiScalingForPage(pageNumber)));
                } else {
                    break;
                }
                jsonIndex++;
            }
            detectedContents.add(pageContents);
        }
        return detectedContents;
    }

    private static void prepareInputForTableTransformerPython(String pdfName, String password, List<List<IObject>> contents,
                                                              File imagesFolder, File wordsFolder) throws IOException {
        generatePageImages(pdfName, password, imagesFolder);
        if (TATR_USES_WORDS) {
            generateJsonWithWords(wordsFolder, contents);
        }
    }

    private static void generatePageImages(String pdfName, String password, File imagesFolder) throws IOException {
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(pdfName, password, true, 1000f)) {
            StaticLayoutContainers.setContrastRatioConsumer(contrastRatioConsumer);
            for (int pageNumber = 0; pageNumber < StaticResources.getDocument().getNumberOfPages(); pageNumber++) {
                BufferedImage image = contrastRatioConsumer.getRenderPage(pageNumber);
                File imageFile = new File(imagesFolder + File.separator + "image" + pageNumber + ".jpg");
                ImageIO.write(image, "jpg", imageFile);
            }
        }
    }

    private static void generateJsonWithWords(File wordsFolder, List<List<IObject>> contents) throws FileNotFoundException {
        for (int pageNumber = 0; pageNumber < StaticResources.getDocument().getNumberOfPages(); pageNumber++) {
            File wordsFile = new File(wordsFolder + File.separator + "image" + pageNumber + "_words.json");
            textContentsToJSON(wordsFile, contents.get(pageNumber), DocumentProcessor.getPageBoundingBox(pageNumber), 
                    StaticLayoutContainers.getContrastRatioConsumer().getDpiScalingForPage(pageNumber));
        }
    }

    public static void textContentsToJSON(File jsonFile, List<? extends IObject> pageContents, BoundingBox pageBoundingBox, 
                                          double dpiScaling) throws FileNotFoundException {
        int spanNumber = 0;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        for (IObject content : pageContents) {
            if (content instanceof TextChunk) {
                BoundingBox scaledBoundingBox = transformBBoxFromPDFToImageCoordinates(content.getBoundingBox(), 
                        pageBoundingBox, dpiScaling);
                stringBuilder.append(String.format("{\"bbox\":[%s, %s, %s, %s], \"text\":\"%s\", \"span_num\":%s},",
                                scaledBoundingBox.getLeftX(), scaledBoundingBox.getBottomY(),
                                scaledBoundingBox.getRightX(), scaledBoundingBox.getTopY(), 
                        ((TextChunk) content).getValue(), spanNumber++));
            }
        }
        if (stringBuilder.length() > 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("]");
        try (PrintWriter out = new PrintWriter(jsonFile)) {
            out.println(stringBuilder);
        }
    }

    public static BoundingBox transformBBoxFromPDFToImageCoordinates(BoundingBox boundingBox, 
            BoundingBox pageBoundingBox, double dpiScaling) {
        return new BoundingBox(boundingBox.getPageNumber(), boundingBox.getLeftX() * dpiScaling,
                (pageBoundingBox.getHeight() - boundingBox.getTopY()) * dpiScaling,
                boundingBox.getRightX() * dpiScaling,
                (pageBoundingBox.getHeight() - boundingBox.getBottomY()) * dpiScaling);
    }
    
    public static BoundingBox transformBBoxFromImageToPDFCoordinates(BoundingBox boundingBox, 
            BoundingBox pageBoundingBox, double dpiScaling) {
        return new BoundingBox(boundingBox.getPageNumber(), boundingBox.getLeftX() / dpiScaling, 
                pageBoundingBox.getHeight() - boundingBox.getTopY() / dpiScaling, 
                boundingBox.getRightX() / dpiScaling, 
                pageBoundingBox.getHeight() - boundingBox.getBottomY() / dpiScaling);
    }
}
