package com.hancom.opendataloader.pdf.processors;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TableTransformerProcessor {

    private static final Logger LOGGER = Logger.getLogger(TableTransformerProcessor.class.getCanonicalName());

    public static void runTableTransformerPython(File scriptFolder, File imagesFolder, File wordsFolder, File resultFolder) 
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "py", "inference.py",
                "--mode", "extract",
//                "--detection_model_path", "./pubtables1m_detection_detr_r18.pth", 
                "--detection_device", "cpu",
//                "--structure_model_path", "./TATR-v1.1-All-msft.pth", 
                "--words_dir", wordsFolder.getAbsolutePath(),
                "--structure_device", "cpu",
                "--image_dir", imagesFolder.getAbsolutePath(),
                "--out_dir", resultFolder.getAbsolutePath(),
                "-zp", "-ol", "--crop_padding", "25");
        builder.directory(scriptFolder);
        builder.redirectOutput(new File("python_output.txt"));
        builder.redirectError(new File("python_error.txt"));
        Process process = builder.start();
        process.waitFor();
    }

    public static List<IObject> parseTableTransformerJson(File jsonFile, int pageNumber, double dpiScaling) {
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
                if (label.equals("table row")) {
                    tableRowsBBoxes.add(transformBBoxFromImageToPDFCoordinates(boundingBox, pageBoundingBox, dpiScaling));
                } else if (label.equals("table column")) {
                    tableColumnsBBoxes.add(transformBBoxFromImageToPDFCoordinates(boundingBox, pageBoundingBox, dpiScaling));
                } else if (label.equals("table spanning cell")) {
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

    private static BoundingBox transformBBoxFromImageToPDFCoordinates(BoundingBox boundingBox, 
                                                                      BoundingBox pageBoundingBox, double dpiScaling) {
        return new BoundingBox(boundingBox.getPageNumber(), boundingBox.getLeftX() / dpiScaling, 
                pageBoundingBox.getHeight() - boundingBox.getTopY() / dpiScaling, 
                boundingBox.getRightX() / dpiScaling, 
                pageBoundingBox.getHeight() - boundingBox.getBottomY() / dpiScaling);
    }

}
