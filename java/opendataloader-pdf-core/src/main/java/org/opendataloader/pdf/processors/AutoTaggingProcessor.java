package org.opendataloader.pdf.processors;

import org.verapdf.as.ASAtom;
import org.verapdf.cos.*;

import org.verapdf.pd.*;
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class AutoTaggingProcessor {

//    private static List<Integer> mcids = new ArrayList<>();
    private static List<COSObject> structParents = new ArrayList<>();

    public static void createTaggedPDF(File inputPDF, String outputFolder, PDDocument document, List<List<IObject>> contents) throws IOException {
        COSDocument cosDocument = document.getDocument();
        PDCatalog catalog = document.getCatalog();
        COSObject structTreeRoot = createStructTreeRoot(catalog, cosDocument, document);
        createStructureTreeElements(contents, structTreeRoot, cosDocument);
//        updatePages(document, cosDocument);
//        createParentTree(cosDocument, structTreeRoot);
        String outputFileName = outputFolder + File.separator +
            inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_tagged.pdf";
        try (OutputStream output = new FileOutputStream(outputFileName)) {
            document.saveTo(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    private static void updatePages(PDDocument document, COSDocument cosDocument) throws IOException {
//        List<org.verapdf.pd.PDPage> rawPages = document.getPages();
//        for (PDPage page : rawPages) {
//            page.getObject().setKey(ASAtom.STRUCT_PARENTS, COSInteger.construct(page.getPageNumber()));
//            cosDocument.addChangedObject(page.getObject());
//            COSObject contentsObject = page.getKey(ASAtom.CONTENTS);
////            byte[] res = new PDFStreamWriter().write(processTokens(getTokens(page.getContent())));
//            try (InputStream inStream = new ByteArrayInputStream(res)) {
//                contentsObject.setData(new ASMemoryInStream(inStream));
//            }
//            contentsObject.setKey(ASAtom.FILTER, new COSObject());
////            ((COSStream)contentsObject.getDirectBase()).setFilters(new COSFilters(COSName.construct(ASAtom.FLATE_DECODE)));
//            cosDocument.addChangedObject(contentsObject);
//        }
//    }

    private static COSObject createStructTreeRoot(PDCatalog catalog, COSDocument cosDocument, PDDocument document) {
        COSObject structTreeRoot = COSIndirect.construct(COSDictionary.construct(), cosDocument);
        catalog.setKey(ASAtom.STRUCT_TREE_ROOT, structTreeRoot);
        structTreeRoot.setKey(ASAtom.TYPE, COSName.construct(ASAtom.STRUCT_TREE_ROOT));
        cosDocument.addObject(structTreeRoot);
        structTreeRoot.setKey(ASAtom.PARENT_TREE_NEXT_KEY, COSInteger.construct(document.getNumberOfPages()));
        cosDocument.addChangedObject(catalog.getObject());
        return structTreeRoot;
    }

//    private static void createParentTree(COSDocument cosDocument, COSObject structTreeRoot) {
//        COSObject parentTree = COSIndirect.construct(COSDictionary.construct(), cosDocument);
//        cosDocument.addObject(parentTree);
//        structTreeRoot.setKey(ASAtom.PARENT_TREE, parentTree);
//        COSObject nums = COSArray.construct();
//        parentTree.setKey(ASAtom.NUMS, nums);
//        nums.add(COSInteger.construct(0));//todo fix
//        COSObject array = COSArray.construct();
//        for (COSObject structParent : structParents) {
//            array.add(structParent);
//        }
//        nums.add(array);
//    }

    private static COSObject addStructElement(COSObject parent, COSDocument cosDocument, String type) {
        COSObject structElement = COSIndirect.construct(COSDictionary.construct(), cosDocument);
        COSObject k = parent.getKey(ASAtom.K);
        if (k.getType() == COSObjType.COS_ARRAY) {
            k.add(structElement);
        } else {
            k = COSArray.construct();
            parent.setKey(ASAtom.K, k);
            k.add(structElement);
        }
        structElement.setKey(ASAtom.S, COSName.construct(type));
        structElement.setKey(ASAtom.TYPE, COSName.construct(ASAtom.STRUCT_ELEM));
        structElement.setKey(ASAtom.P, parent);
        structElement.setKey(ASAtom.PG, cosDocument.getPDDocument().getPages().get(0).getObject());//todo calculate page number
        cosDocument.addObject(structElement);
        return structElement;
    }


    public static void createStructureTreeElements(List<List<IObject>> contents, COSObject structTreeRoot, COSDocument cosDocument) {
        COSObject seDocument = addStructElement(structTreeRoot, cosDocument, TaggedPDFConstants.DOCUMENT);
        for (List<IObject> pageContents : contents) {
            for (IObject content : pageContents) {
                createStructElem(content, seDocument, cosDocument);
            }
        }
    }

    private static void createStructElem(IObject object, COSObject parentStructElem, COSDocument cosDocument) {
        if (object instanceof SemanticHeading) {
            createHeadingStructElem((SemanticHeading) object, parentStructElem, cosDocument);
        } else if (object instanceof SemanticParagraph) {
            createParagraphStructElem((SemanticParagraph) object, parentStructElem, cosDocument);
        } else if (object instanceof SemanticCaption) {
            createCaptionStructElem((SemanticCaption) object, parentStructElem, cosDocument);
        } else if (object instanceof PDFList) {
            createListStructElem((PDFList) object, parentStructElem, cosDocument);
        } else if (object instanceof TableBorder) {
            createTableStructElem((TableBorder) object, parentStructElem, cosDocument);
        } else if (object instanceof ImageChunk) {
            createFigureStructElem((ImageChunk) object, parentStructElem, cosDocument);
        }
    }

    private static void createHeadingStructElem(SemanticHeading heading, COSObject parent, COSDocument cosDocument) {
        COSObject headingObject = addStructElement(parent, cosDocument, TaggedPDFConstants.H + heading.getHeadingLevel());
//        processTextNode(heading, headingObject);
    }

    private static void createParagraphStructElem(SemanticParagraph paragraph, COSObject parent, COSDocument cosDocument) {
        COSObject paragraphObject = addStructElement(parent, cosDocument, TaggedPDFConstants.P);
//        processTextNode(paragraph, paragraphObject);
    }

    private static void createCaptionStructElem(SemanticCaption caption, COSObject parent, COSDocument cosDocument) {
        COSObject captionObject = addStructElement(parent, cosDocument, TaggedPDFConstants.CAPTION);
//        processTextNode(caption, captionObject);
    }

    private static void createFigureStructElem(ImageChunk image, COSObject parent, COSDocument cosDocument) {
        COSObject figureObject = addStructElement(parent, cosDocument, TaggedPDFConstants.FIGURE);
        double[] bbox = {image.getLeftX(), image.getBottomY(), image.getRightX(), image.getTopY()};
        figureObject.setKey(ASAtom.BBOX, COSArray.construct(4, bbox));
        //TODO: process image, add height and width attributes
    }

    private static void createListStructElem(PDFList list, COSObject parent, COSDocument cosDocument) {
        COSObject listObject = addStructElement(parent, cosDocument, TaggedPDFConstants.L);
        if (list.getNextList() != null) {
            listObject.setKey(ASAtom.ID, COSString.construct(String.valueOf(list.getRecognizedStructureId()).getBytes()));
        }
        if (list.getPreviousList() != null) {
            listObject.setKey(ASAtom.CONTINUED_LIST, COSBoolean.construct(true));
            listObject.setKey(ASAtom.CONTINUED_FROM, COSString.construct(String.valueOf(list.getPreviousList().getRecognizedStructureId()).getBytes()));
        }
        listObject.setKey(ASAtom.LIST_NUMBERING, COSName.construct(ListProcessor.getListNumbering(list.getNumberingStyle())));

        for (ListItem listItem : list.getListItems()) {
            COSObject listItemObject = addStructElement(listObject, cosDocument, TaggedPDFConstants.LI);
            if (listItem.getLabelLength() > 0) {
                COSObject lblObject = addStructElement(listItemObject, cosDocument, TaggedPDFConstants.LBL);
                SemanticTextNode lblTextNode = new SemanticTextNode();
                lblTextNode.add(new TextLine(listItem.getFirstLine(), 0, listItem.getLabelLength()));
                //processTextNode(lblTextNode, lblObject);
            }
            COSObject lBodyObject = addStructElement(listItemObject, cosDocument, TaggedPDFConstants.LBODY);
            SemanticTextNode lBodyTextNode = new SemanticTextNode();
            for (TextLine line : listItem.getLines()) {
                lBodyTextNode.add(line);
            }
            lBodyTextNode.setFirstLine(new TextLine(listItem.getFirstLine(), listItem.getLabelLength(), listItem.getFirstLine().getValue().length()));
            //processTextNode(lBodyTextNode, lBodyObject);
            for (IObject content : listItem.getContents()) {
                createStructElem(content, lBodyObject, cosDocument);
            }
        }
    }

    private static void createTableStructElem(TableBorder table, COSObject parent, COSDocument cosDocument) {
        COSObject tableObject = addStructElement(parent, cosDocument, TaggedPDFConstants.TABLE);
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            COSObject rowObject = addStructElement(tableObject, cosDocument, TaggedPDFConstants.TR);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    COSObject cellObject = addStructElement(rowObject, cosDocument, TaggedPDFConstants.TD);
                    cellObject.setKey(ASAtom.COL_SPAN, COSInteger.construct(cell.getColSpan()));
                    cellObject.setKey(ASAtom.ROW_SPAN, COSInteger.construct(cell.getRowSpan()));
                    for (IObject cellContent : cell.getContents()) {
                        createStructElem(cellContent, cellObject, cosDocument);
                    }
                }
            }
        }
    }

//    protected static List<Object> getTokens(PDContentStream pdContentStream) {
//        if (pdContentStream != null) {
//            try {
//                COSObject contentStream = pdContentStream.getContents();
//                if (contentStream.getType() == COSObjType.COS_STREAM || contentStream.getType() == COSObjType.COS_ARRAY) {
//                    try (ASInputStream opStream = contentStream.getDirectBase().getData(COSStream.FilterFlags.DECODE)) {
//                        try (PDFStreamParser streamParser = new PDFStreamParser(opStream)) {
//                            streamParser.parseTokens();
//                            return streamParser.getTokens();
//                        }
//                    }
//                }
//            } catch (IOException e) {
//            }
//        }
//        return Collections.emptyList();
//    }

//    private static void processTextNode(SemanticTextNode textNode, COSObject cosObject) {
//        List<Integer> mcids = new ArrayList<>();
//        for (TextColumn textColumn : textNode.getColumns()) {
//            for (TextLine textLine : textColumn.getLines()) {
//                for (TextChunk textChunk : textLine.getTextChunks()) {
//                    mcids.addAll(textChunk.getErrorCodes());
//                }
//            }
//        }
//        AutoTaggingProcessor.mcids.addAll(mcids);
//        for (int index : mcids) {
//            structParents.add(cosObject);
//        }
//        if (!mcids.isEmpty()) {
//            COSObject array = COSArray.construct();
//            cosObject.setKey(ASAtom.K, array);
//            for (Integer mcid : mcids) {
//                array.add(COSInteger.construct(AutoTaggingProcessor.mcids.indexOf(mcid)));
//            }
//        }
//    }

//    protected static List<Object> processTokens(List<Object> processTokens) {
//        List<Object> result = new ArrayList<>();
//        List<Object> arguments = new ArrayList<>();
//        for (int index = 0; index < processTokens.size(); index++) {
//            Object token = processTokens.get(index);
//            if (token instanceof COSBase) {
//                arguments.add(token);
//            } else if (token instanceof Operator) {
//                if (Operators.BDC.equals(((Operator) token).getOperator()) ||
//                    Operators.EMC.equals(((Operator) token).getOperator()) ||
//                    Operators.BMC.equals(((Operator) token).getOperator())) {
//                    arguments.clear();
//                    continue;
//                }
//                if (mcids.contains(index)) {
//                    result.add(COSName.construct("Span").getDirectBase());
//                    COSObject dictionary = COSDictionary.construct();
//                    dictionary.setKey(ASAtom.MCID, COSInteger.construct(mcids.indexOf(index)));
//                    result.add(dictionary.getDirectBase());
//                    result.add(Operator.getOperator(Operators.BDC));
//                }
//                result.addAll(arguments);
//                arguments.clear();
//                result.add(token);
//                if (mcids.contains(index)) {
//                    result.add(Operator.getOperator(Operators.EMC));
//                }
//            }
//        }
//        return result;
//    }
}
