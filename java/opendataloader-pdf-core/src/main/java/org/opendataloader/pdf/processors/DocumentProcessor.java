/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.json.JsonWriter;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.opendataloader.pdf.markdown.MarkdownGeneratorFactory;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.html.HtmlGeneratorFactory;
import org.opendataloader.pdf.pdf.PDFWriter;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.text.TextGenerator;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.containers.StaticCoreContainers;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.containers.StaticStorages;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.gf.model.impl.sa.GFSAPDFDocument;
import org.verapdf.parser.PDFFlavour;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());
    public static void processFile(String inputPdfName, Config config) throws IOException {
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        List<List<IObject>> contents = StaticLayoutContainers.isUseStructTree() ?
            TaggedDocumentProcessor.processDocument(inputPdfName, config) :
            processDocument(inputPdfName, config);
        sortContents(contents, config);
        generateOutputs(inputPdfName, contents, config);
    }

    private static List<List<IObject>> processDocument(String inputPdfName, Config config) throws IOException {
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = ContentFilterProcessor.getFilteredContents(inputPdfName,
                StaticContainers.getDocument().getArtifacts(pageNumber), pageNumber, config);
            contents.add(pageContents);
        }
        if (config.isClusterTableMethod()) {
            new ClusterTableProcessor().processTables(contents);
        }
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = TableBorderProcessor.processTableBorders(contents.get(pageNumber), pageNumber);
            pageContents = pageContents.stream().filter(x -> !(x instanceof LineChunk)).collect(Collectors.toList());
            pageContents = TextLineProcessor.processTextLines(pageContents);
            pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
            contents.set(pageNumber, pageContents);
        }
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);
        ListProcessor.processLists(contents, false);
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            pageContents = ListProcessor.processListsFromTextNodes(pageContents);
            HeadingProcessor.processHeadings(pageContents, false);
            setIDs(pageContents);
            CaptionProcessor.processCaptions(pageContents);
            contents.set(pageNumber, pageContents);
        }
        ListProcessor.checkNeighborLists(contents);
        TableBorderProcessor.checkNeighborTables(contents);
        HeadingProcessor.detectHeadingsLevels();
        LevelProcessor.detectLevels(contents);
        return contents;
    }

    private static void generateOutputs(String inputPdfName, List<List<IObject>> contents, Config config) throws IOException {
        File inputPDF = new File(inputPdfName);
        new File(config.getOutputFolder()).mkdirs();
        if (config.isGenerateHtml() || config.isAddImageToMarkdown() || config.isGenerateJSON()) {
            String fileName = Paths.get(inputPdfName).getFileName().toString();
            StaticLayoutContainers.setImagesDirectory(config.getOutputFolder() + File.separator + fileName.substring(0, fileName.length() - 4) + "_images");
            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.write(contents, inputPdfName, config.getPassword());
        }
        if (config.isGeneratePDF()) {
            PDFWriter pdfWriter = new PDFWriter();
            pdfWriter.updatePDF(inputPDF, config.getPassword(), config.getOutputFolder(), contents);
        }
        if (config.isGenerateJSON()) {
            JsonWriter.writeToJson(inputPDF, config.getOutputFolder(), contents);
        }
        if (config.isGenerateMarkdown()) {
            try (MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF,
                config.getOutputFolder(), config)) {
                markdownGenerator.writeToMarkdown(contents);
            }
        }
        if (config.isGenerateHtml()) {
            try (HtmlGenerator htmlGenerator = HtmlGeneratorFactory.getHtmlGenerator(inputPDF, config.getOutputFolder(), config)) {
                htmlGenerator.writeToHtml(contents);
            }
        }
        if (config.isGenerateText()) {
            try (TextGenerator textGenerator = new TextGenerator(inputPDF, config.getOutputFolder(), config)) {
                textGenerator.writeToText(contents);
            }
        }
    }

    public static void preprocessing(String pdfName, Config config) throws IOException {
        LOGGER.log(Level.INFO, () -> "File name: " + pdfName);
        updateStaticContainers(config);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
//        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2_HUMAN));
        StaticStorages.setIsFilterInvisibleLayers(config.getFilterConfig().isFilterHiddenOCG());
        StaticContainers.setDocument(document);
        if (config.isUseStructTree()) {
            document.parseStructureTreeRoot();
            if (document.getTree() != null) {
                StaticLayoutContainers.setIsUseStructTree(true);
            } else {
                StaticLayoutContainers.setIsUseStructTree(false);
                LOGGER.log(Level.WARNING, "The document has no structure tree. The 'use-struct-tree' option will be ignored.");
            }
        }
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setIsFontProgramsParsing(true);
        StaticStorages.setIsIgnoreMCIDs(!StaticLayoutContainers.isUseStructTree());
        StaticStorages.setIsAddSpacesBetweenTextPieces(true);
        document.parseChunks();
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }

    private static void updateStaticContainers(Config config) {
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticCoreContainers.clearAllContainers();
        StaticXmpCoreContainers.clearAllContainers();
        StaticContainers.setKeepLineBreaks(config.isKeepLineBreaks());
        StaticLayoutContainers.setCurrentContentId(1);
        StaticResources.setPassword(config.getPassword());
    }

    public static void setIDs(List<IObject> contents) {
        for (IObject object : contents) {
            object.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        }
    }

    public static void setIndexesForDocumentContents(List<List<IObject>> contents) {
        for (List<IObject> pageContents : contents) {
            setIndexesForContentsList(pageContents);
        }
    }

    public static void setIndexesForContentsList(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            contents.get(index).setIndex(index);
        }
    }

    public static List<IObject> removeNullObjectsFromList(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        for (IObject content : contents) {
            if (content != null) {
                newContents.add(content);
            }
        }
        return newContents;
    }

    private static void calculateDocumentInfo() {
        PDDocument document = StaticResources.getDocument();
        LOGGER.log(Level.INFO, () -> "Number of pages: " + document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        GFCosInfo info = getInfo(trailer);
        LOGGER.log(Level.INFO, () -> "Author: " + (info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator()));
        LOGGER.log(Level.INFO, () -> "Title: " + (info.getTitle() != null ? info.getTitle() : info.getXMPTitle()));
        LOGGER.log(Level.INFO, () -> "Creation date: " + (info.getCreationDate() != null ? info.getCreationDate() : info.getXMPCreateDate()));
        LOGGER.log(Level.INFO, () -> "Modification date: " + (info.getModDate() != null ? info.getModDate() : info.getXMPModifyDate()));
    }

    private static GFCosInfo getInfo(COSTrailer trailer) {
        COSObject object = trailer.getKey(ASAtom.INFO);
        return new GFCosInfo((COSDictionary) (object != null && object.getType() == COSObjType.COS_DICT ? object.getDirectBase() : COSDictionary.construct().get()));
    }

    public static String getContentsValueForTextNode(SemanticTextNode textNode) {
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"",
                textNode.getSemanticType().getValue(), textNode.getFontName(),
                textNode.getFontSize(), Arrays.toString(textNode.getTextColor()),
                textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

    public static BoundingBox getPageBoundingBox(int pageNumber) {
        PDDocument document = StaticResources.getDocument();
        if (document == null) {
            return null;
        }
        double[] cropBox = document.getPage(pageNumber).getCropBox();
        if (cropBox == null) {
            return null;
        }
        return new BoundingBox(pageNumber, cropBox);
    }

    public static List<IObject> sortPageContents(List<IObject> contents) {
        List<IObject> sortedContents = new ArrayList<>(contents);
        sortedContents.sort((o1, o2) -> {
            BoundingBox b1 = o1.getBoundingBox();
            BoundingBox b2 = o2.getBoundingBox();
            if (!Objects.equals(b1.getPageNumber(), b2.getPageNumber())) {
                return b1.getPageNumber() - b2.getPageNumber();
            }
            if (!Objects.equals(b1.getLastPageNumber(), b2.getLastPageNumber())) {
                return b1.getLastPageNumber() - b2.getLastPageNumber();
            }
            if (!Objects.equals(b1.getTopY(), b2.getTopY())) {
                return b2.getTopY() - b1.getTopY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getLeftX(), b2.getLeftX())) {
                return b2.getLeftX() - b1.getLeftX() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getBottomY(), b2.getBottomY())) {
                return b1.getBottomY() - b2.getBottomY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getRightX(), b2.getRightX())) {
                return b1.getRightX() - b2.getRightX() > 0 ? 1 : -1;
            }
            return 0;
        });
        return sortedContents;
    }

    public static void sortContents(List<List<IObject>> contents, Config config) {
        if (Config.READING_ORDER_BY_BBOX.equals(config.getReadingOrder())) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                contents.set(pageNumber, sortPageContents(contents.get(pageNumber)));
            }
        }
    }
}
