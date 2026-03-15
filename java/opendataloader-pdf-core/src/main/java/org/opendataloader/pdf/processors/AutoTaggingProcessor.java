package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.autotagging.OperatorStreamKey;
import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASMemoryInStream;
import org.verapdf.cos.*;

import org.verapdf.gf.model.factory.chunks.GraphicsState;
import org.verapdf.gf.model.impl.sa.util.ResourceHandler;
import org.verapdf.pd.*;
import org.verapdf.pd.images.PDXObject;
import org.verapdf.tools.StaticResources;
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.io.*;
import java.util.*;

public class AutoTaggingProcessor {

    private static final Map<OperatorStreamKey, Map<Integer, Set<StreamInfo>>> operatorIndexesToStreamInfosMap = new HashMap<>();
    private static final Map<OperatorStreamKey, List<COSObject>> structParents = new HashMap<>();
    private static final Map<OperatorStreamKey, Integer> structParentsIntegers = new HashMap<>();
    private static boolean isPDF2_0 = false;
    private static final int MAX_TOKENS_PER_STREAM = 100_000;

    public static void createTaggedPDF(File inputPDF, String outputFolder, PDDocument document, List<List<IObject>> contents) throws IOException {
        operatorIndexesToStreamInfosMap.clear();
        structParents.clear();
        structParentsIntegers.clear();
        if (document.getVersion() == 2.0F) {
            isPDF2_0 = true;
        }
        COSDocument cosDocument = document.getDocument();
        PDCatalog catalog = document.getCatalog();
        COSObject structTreeRoot = createStructTreeRoot(catalog, cosDocument, document);
        createStructureTreeElements(contents, structTreeRoot, cosDocument);
        updatePages(document, cosDocument);
        createParentTree(cosDocument, structTreeRoot);
        String outputFileName = outputFolder + File.separator +
            inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_tagged.pdf";
        try (OutputStream output = new FileOutputStream(outputFileName)) {
            document.saveTo(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void updatePages(PDDocument document, COSDocument cosDocument) throws IOException {
        int currentStructParent = 0;
        for (OperatorStreamKey operatorStreamKey : structParents.keySet()) {
            structParentsIntegers.put(operatorStreamKey, currentStructParent++);
        }
        List<org.verapdf.pd.PDPage> rawPages = document.getPages();
        for (int pageNumber = 0; pageNumber < rawPages.size(); pageNumber++) {
            PDPage page = rawPages.get(pageNumber);
            OperatorStreamKey operatorStreamKey = new OperatorStreamKey(pageNumber, null);
            Integer structParent = structParentsIntegers.get(operatorStreamKey);
            if (structParent == null) {
                continue;
            }
            page.getObject().setKey(ASAtom.STRUCT_PARENTS, COSInteger.construct(structParent));
            cosDocument.addChangedObject(page.getObject());
            COSObject contentsObject = page.getKey(ASAtom.CONTENTS);
            ResourceHandler resourceHandler = ResourceHandler.getInstance(page.getResources());
            List<Object> processedTokens = new ChunksWriter(new GraphicsState(resourceHandler),
                resourceHandler).processTokens(ChunksWriter.getTokens(page.getContent()), operatorStreamKey);
            if (processedTokens.size() <= MAX_TOKENS_PER_STREAM) {
                if (contentsObject != null && contentsObject.isIndirect() != null && contentsObject.isIndirect()) {
                    setUpContents(contentsObject, processedTokens);
                    cosDocument.addChangedObject(contentsObject);
                } else {
                    createContentsIndirect(cosDocument, processedTokens);
                }
            } else {
                COSObject streamsArray = COSArray.construct();
                for (int start = 0; start < processedTokens.size(); start += MAX_TOKENS_PER_STREAM) {
                    int end = Math.min(start + MAX_TOKENS_PER_STREAM, processedTokens.size());
                    List<Object> chunk = processedTokens.subList(start, end);
                    COSObject streamIndirect = createContentsIndirect(cosDocument, chunk);
                    streamsArray.add(streamIndirect);
                }
                page.getObject().setKey(ASAtom.CONTENTS, streamsArray);
            }
        }
    }

    private static COSObject createContentsIndirect(COSDocument cosDocument, List<Object> tokens) throws IOException {
        COSObject streamObj = COSIndirect.construct(COSStream.construct(), cosDocument);
        setUpContents(streamObj, tokens);
        cosDocument.addObject(streamObj);
        return streamObj;
    }

    public static void setUpContents(COSObject contentsObj, List<Object> tokens) throws IOException {
        byte[] res = new PDFStreamWriter().write(tokens);
        try (InputStream inStream = new ByteArrayInputStream(res)) {
            contentsObj.setData(new ASMemoryInStream(inStream));
        }
        contentsObj.setKey(ASAtom.FILTER, new COSObject());
        COSStream newStream = (COSStream) contentsObj.getDirectBase();
        newStream.setFilters(new COSFilters(COSName.construct(ASAtom.FLATE_DECODE)));
    }

    private static COSObject createStructTreeRoot(PDCatalog catalog, COSDocument cosDocument, PDDocument document) {
        COSObject structTreeRoot = COSIndirect.construct(COSDictionary.construct(), cosDocument);
        catalog.setKey(ASAtom.STRUCT_TREE_ROOT, structTreeRoot);
        structTreeRoot.setKey(ASAtom.TYPE, COSName.construct(ASAtom.STRUCT_TREE_ROOT));
        cosDocument.addObject(structTreeRoot);
        structTreeRoot.setKey(ASAtom.PARENT_TREE_NEXT_KEY, COSInteger.construct(document.getNumberOfPages()));
        cosDocument.addChangedObject(catalog.getObject());
        return structTreeRoot;
    }

    private static void createParentTree(COSDocument cosDocument, COSObject structTreeRoot) {
        COSObject parentTree = COSIndirect.construct(COSDictionary.construct(), cosDocument);
        cosDocument.addObject(parentTree);
        structTreeRoot.setKey(ASAtom.PARENT_TREE, parentTree);
        COSObject nums = COSArray.construct();
        parentTree.setKey(ASAtom.NUMS, nums);
        for (Map.Entry<OperatorStreamKey, List<COSObject>> entry : structParents.entrySet()) {
            nums.add(COSInteger.construct(structParentsIntegers.get(entry.getKey())));
            COSObject array = COSArray.construct();
            for (COSObject structParent : entry.getValue()) {
                array.add(structParent);
            }
            nums.add(array);
        }
    }

    private static COSObject addStructElement(COSObject parent, COSDocument cosDocument, String type, Integer pageNumber) {
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
        if (pageNumber != null) {
            structElement.setKey(ASAtom.PG, cosDocument.getPDDocument().getPages().get(pageNumber).getObject());
        }
        cosDocument.addObject(structElement);
        return structElement;
    }


    public static void createStructureTreeElements(List<List<IObject>> contents, COSObject structTreeRoot, COSDocument cosDocument) {
        COSObject seDocument = addStructElement(structTreeRoot, cosDocument, TaggedPDFConstants.DOCUMENT, null);
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
            TableBorder table = (TableBorder) object;
            if (table.isTextBlock()) {
                createPartStructElemForTextBlock(table, parentStructElem, cosDocument);
            } else if (!table.isOneCellTable()) {
                createTableStructElem(table, parentStructElem, cosDocument);
            }
        } else if (object instanceof ImageChunk) {
            createFigureStructElem((ImageChunk) object, parentStructElem, cosDocument);
        }
    }

    private static void createHeadingStructElem(SemanticHeading heading, COSObject parent, COSDocument cosDocument) {
        COSObject headingObject = addStructElement(parent, cosDocument,
            isPDF2_0 ? TaggedPDFConstants.H + heading.getHeadingLevel() : TaggedPDFConstants.H,
            heading.getPageNumber());
        processTextNode(heading, headingObject);
    }

    private static void createParagraphStructElem(SemanticParagraph paragraph, COSObject parent, COSDocument cosDocument) {
        COSObject paragraphObject = addStructElement(parent, cosDocument, TaggedPDFConstants.P, paragraph.getPageNumber());
        processTextNode(paragraph, paragraphObject);
    }

    private static void createCaptionStructElem(SemanticCaption caption, COSObject parent, COSDocument cosDocument) {
        COSObject captionObject = addStructElement(parent, cosDocument, TaggedPDFConstants.CAPTION, caption.getPageNumber());
        processTextNode(caption, captionObject);
    }

    private static void createFigureStructElem(ImageChunk image, COSObject parent, COSDocument cosDocument) {
        COSObject figureObject = addStructElement(parent, cosDocument, TaggedPDFConstants.FIGURE, image.getPageNumber());
        double[] bbox = {image.getLeftX(), image.getBottomY(), image.getRightX(), image.getTopY()};
        addAttributeToStructElem(figureObject, ASAtom.LAYOUT, ASAtom.BBOX, COSArray.construct(4, bbox));
        processImageNode(image, figureObject);
        //TODO: add height and width attributes
    }

    private static void createListStructElem(PDFList list, COSObject parent, COSDocument cosDocument) {
        COSObject listObject = addStructElement(parent, cosDocument, TaggedPDFConstants.L, list.getPageNumber());
        if (list.getNextList() != null) {
            listObject.setKey(ASAtom.ID, COSString.construct(String.valueOf(list.getRecognizedStructureId()).getBytes()));
        }
        if (list.getPreviousList() != null) {
            addAttributeToStructElem(listObject, ASAtom.LIST, ASAtom.CONTINUED_LIST, COSBoolean.construct(true));
            addAttributeToStructElem(listObject, ASAtom.LIST, ASAtom.CONTINUED_FROM,
                COSString.construct(String.valueOf(list.getPreviousList().getRecognizedStructureId()).getBytes()));
        }
        addAttributeToStructElem(listObject, ASAtom.LIST, ASAtom.LIST_NUMBERING,
            COSName.construct(ListProcessor.getListNumbering(list.getNumberingStyle())));

        for (ListItem listItem : list.getListItems()) {
            COSObject listItemObject = addStructElement(listObject, cosDocument, TaggedPDFConstants.LI, listItem.getPageNumber());
            int labelLength = listItem.getLabelLength();
            if (labelLength > 0) {
                COSObject lblObject = addStructElement(listItemObject, cosDocument, TaggedPDFConstants.LBL, listItem.getPageNumber());
                SemanticTextNode lblTextNode = new SemanticTextNode();
                lblTextNode.add(new TextLine(listItem.getFirstLine(), 0, listItem.getLabelLength()));
                processTextNode(lblTextNode, lblObject);
            }
            COSObject lBodyObject = addStructElement(listItemObject, cosDocument, TaggedPDFConstants.LBODY, listItem.getPageNumber());
            SemanticTextNode lBodyTextNode = new SemanticTextNode();
            for (TextLine line : listItem.getLines()) {
                lBodyTextNode.add(line);
            }
            if (labelLength > 0) {
                lBodyTextNode.setFirstLine(new TextLine(listItem.getFirstLine(), listItem.getLabelLength(),
                    listItem.getFirstLine().getValue().length()));
            }
            processTextNode(lBodyTextNode, lBodyObject);
            for (IObject content : listItem.getContents()) {
                createStructElem(content, lBodyObject, cosDocument);
            }
        }
    }

    private static void createTableStructElem(TableBorder table, COSObject parent, COSDocument cosDocument) {
        COSObject tableObject = addStructElement(parent, cosDocument, TaggedPDFConstants.TABLE, table.getPageNumber());
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            COSObject rowObject = addStructElement(tableObject, cosDocument, TaggedPDFConstants.TR, row.getPageNumber());
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    COSObject cellObject = addStructElement(rowObject, cosDocument, TaggedPDFConstants.TD, cell.getPageNumber());
                    if (cell.getColSpan() != 1) {
                        addAttributeToStructElem(cellObject, ASAtom.TABLE, ASAtom.COL_SPAN, COSInteger.construct(cell.getColSpan()));
                    }
                    if (cell.getRowSpan() != 1) {
                        addAttributeToStructElem(cellObject, ASAtom.TABLE, ASAtom.ROW_SPAN, COSInteger.construct(cell.getRowSpan()));
                    }
                    for (IObject cellContent : cell.getContents()) {
                        createStructElem(cellContent, cellObject, cosDocument);
                    }
                }
            }
        }
    }

    private static void createPartStructElemForTextBlock(TableBorder table, COSObject parent, COSDocument cosDocument) {
        COSObject partObject = addStructElement(parent, cosDocument, TaggedPDFConstants.PART, table.getPageNumber());
        TableBorderCell cell = table.getCell(0,0);
        for (IObject cellContent : cell.getContents()) {
            createStructElem(cellContent, partObject, cosDocument);
        }
    }

    private static void addAttributeToStructElem(COSObject structElement, ASAtom ownerASAtom, ASAtom attributeName,
                                                 COSObject attributeValue) {
        COSObject aObject = structElement.getKey(ASAtom.A);
        COSObject owner = COSName.construct(ownerASAtom);
        if (aObject.empty()) {
            aObject = COSDictionary.construct();
            aObject.setKey(ASAtom.O, owner);
            aObject.setKey(attributeName, attributeValue);
        } else if (aObject.getType() == COSObjType.COS_DICT) {
            COSObject ownerObject = aObject.getKey(ASAtom.O);
            if (owner.equals(ownerObject)) {
                aObject.setKey(attributeName, attributeValue);
            } else {
                COSObject previousADictionary = aObject;
                aObject = COSArray.construct();
                aObject.add(previousADictionary);
                addAttributeDictionaryToArray(owner, attributeName, attributeValue, aObject);
            }
        } else if (aObject.getType() == COSObjType.COS_ARRAY) {
            boolean isAttributeSet = false;
            for (COSObject dictionary : (COSArray) aObject.getDirectBase()) {
                if (owner.equals(dictionary.getKey(ASAtom.O))) {
                    dictionary.setKey(attributeName, attributeValue);
                    isAttributeSet = true;
                    break;
                }
            }
            if (!isAttributeSet) {
                addAttributeDictionaryToArray(owner, attributeName, attributeValue, aObject);
            }
        }
        structElement.setKey(ASAtom.A, aObject);
    }

    private static void addAttributeDictionaryToArray(COSObject owner, ASAtom attributeName, COSObject attributeValue,
                                                      COSObject aObject) {
        COSObject newADictionary = COSDictionary.construct();
        newADictionary.setKey(ASAtom.O, owner);
        newADictionary.setKey(attributeName, attributeValue);
        aObject.add(newADictionary);
    }

    private static void processTextNode(SemanticTextNode textNode, COSObject cosObject) {
        List<StreamInfo> streamInfos = new ArrayList<>();
        for (TextColumn textColumn : textNode.getColumns()) {
            for (TextLine textLine : textColumn.getLines()) {
                for (TextChunk textChunk : textLine.getTextChunks()) {
                    streamInfos.addAll(textChunk.getStreamInfos());
                }
            }
        }
        addMcidChildren(streamInfos, textNode.getPageNumber(), cosObject);
    }

    private static void processImageNode(ImageChunk imageChunk, COSObject cosObject) {
        addMcidChildren(imageChunk.getStreamInfos(), imageChunk.getPageNumber(), cosObject);
    }

    private static void addMcidChildren(List<StreamInfo> streamInfos, Integer pageNumber, COSObject cosObject) {
        COSObject array = COSArray.construct();
        if (streamInfos.isEmpty()) {
            return;
        }
        cosObject.setKey(ASAtom.K, array);
        List<StreamInfo> streamInfoList = getMergedStreamInfos(streamInfos);
        for (StreamInfo streamInfo : streamInfoList) {
            OperatorStreamKey operatorStreamKey = new OperatorStreamKey(pageNumber, streamInfo.getXObjectName());
            List<COSObject> list = structParents.computeIfAbsent(operatorStreamKey, x -> new ArrayList<>());
            int mcid = list.size();
            COSObject mcidObject = COSInteger.construct(mcid);
            streamInfo.setMcid(mcid);
            operatorIndexesToStreamInfosMap.computeIfAbsent(operatorStreamKey, x -> new HashMap<>())
                .computeIfAbsent(streamInfo.getOperatorIndex(), x -> new TreeSet<>()).add(streamInfo);
            list.add(cosObject);
            if (streamInfo.getXObjectName() != null) {
                PDXObject pdxObject = StaticResources.getDocument().getPage(pageNumber).getResources()
                    .getXObject(ASAtom.getASAtom(streamInfo.getXObjectName()));
                COSObject mcrDictionary = COSDictionary.construct();
                mcrDictionary.setKey(ASAtom.TYPE, COSName.construct(ASAtom.MCR));
                mcrDictionary.setKey(ASAtom.MCID, mcidObject);
                mcrDictionary.setKey(ASAtom.STM, pdxObject.getObject());
                array.add(mcrDictionary);
            } else {
                array.add(mcidObject);
            }
        }
    }

    private static List<StreamInfo> getMergedStreamInfos(List<StreamInfo> streamInfos) {
        List<StreamInfo> streamInfoList = new ArrayList<>();
        Iterator<StreamInfo> streamInfoIterator = streamInfos.iterator();
        StreamInfo previousInfo = streamInfoIterator.next();
        streamInfoList.add(previousInfo);
        while (streamInfoIterator.hasNext()) {
            StreamInfo currentStreamInfo = streamInfoIterator.next();
            if (previousInfo.getOperatorIndex() == currentStreamInfo.getOperatorIndex() &&
                previousInfo.getEndIndex() == currentStreamInfo.getStartIndex()) {
                previousInfo.setEndIndex(currentStreamInfo.getEndIndex());
            } else {
                streamInfoList.add(currentStreamInfo);
                previousInfo = currentStreamInfo;
            }
        }
        return streamInfoList;
    }

    public static Map<OperatorStreamKey, Integer> getStructParentsIntegers() {
        return structParentsIntegers;
    }

    public static Map<OperatorStreamKey, List<COSObject>> getStructParents() {
        return structParents;
    }

    public static Map<OperatorStreamKey, Map<Integer, Set<StreamInfo>>> getOperatorIndexesToStreamInfosMap() {
        return operatorIndexesToStreamInfosMap;
    }
}
