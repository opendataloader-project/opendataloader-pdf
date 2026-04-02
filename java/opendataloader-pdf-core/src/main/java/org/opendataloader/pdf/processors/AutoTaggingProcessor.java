package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.autotagging.OperatorStreamKey;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASMemoryInStream;
import org.verapdf.cos.*;

import org.verapdf.gf.model.factory.chunks.GraphicsState;
import org.verapdf.gf.model.impl.sa.util.ResourceHandler;
import org.verapdf.pd.*;
import org.verapdf.pd.actions.PDAction;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AutoTaggingProcessor {

    private static final Map<OperatorStreamKey, Map<Integer, Set<StreamInfo>>> operatorIndexesToStreamInfosMap = new HashMap<>();
    private static final Map<OperatorStreamKey, List<COSObject>> structParents = new HashMap<>();
    private static final Map<OperatorStreamKey, Integer> structParentsIntegers = new HashMap<>();
    // annotation StructParent entries: int key -> single struct element (Link)
    private static final Map<Integer, COSObject> annotationStructParents = new HashMap<>();
    private static boolean isPDF2_0 = false;
    private static final int MAX_TOKENS_PER_STREAM = 100_000;

    public static synchronized void createTaggedPDF(File inputPDF, String outputFolder, PDDocument document, List<List<IObject>> contents) throws IOException {
        operatorIndexesToStreamInfosMap.clear();
        structParents.clear();
        structParentsIntegers.clear();
        annotationStructParents.clear();
        imageChunkFigureCounter = 0;
        if (document.getVersion() == 2.0F) {
            isPDF2_0 = true;
        }
        COSDocument cosDocument = document.getDocument();
        PDCatalog catalog = document.getCatalog();
        COSObject structTreeRoot = createStructTreeRoot(catalog, cosDocument, document);
        COSObject seDocument = createStructureTreeElements(contents, structTreeRoot, cosDocument);
        updatePages(document, cosDocument);
        createLinkAnnotationStructElements(document, cosDocument, seDocument);
        createParentTree(cosDocument, structTreeRoot);
        String outputFileName = outputFolder + File.separator +
            inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_tagged.pdf";
        document.saveAs(outputFileName);
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
            if (structParent != null) {
                page.getObject().setKey(ASAtom.STRUCT_PARENTS, COSInteger.construct(structParent));
                cosDocument.addChangedObject(page.getObject());
            }
            COSObject contentsObject = page.getKey(ASAtom.CONTENTS);
            ResourceHandler resourceHandler = ResourceHandler.getInstance(page.getResources());
            List<Object> processedTokens = new ChunksWriter(new GraphicsState(resourceHandler),
                resourceHandler).processTokens(ChunksWriter.getTokens(page.getContent()), operatorStreamKey);
            if (processedTokens.size() <= MAX_TOKENS_PER_STREAM) {
                if (contentsObject != null && contentsObject.isIndirect() != null && contentsObject.isIndirect()) {
                    setUpContents(contentsObject, processedTokens);
                    cosDocument.addChangedObject(contentsObject);
                } else {
                    page.getObject().setKey(ASAtom.CONTENTS, createContentsIndirect(cosDocument, processedTokens));
                    cosDocument.addChangedObject(page.getObject());
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
                cosDocument.addChangedObject(page.getObject());
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
        int nextKey = 0;
        for (Map.Entry<OperatorStreamKey, List<COSObject>> entry : structParents.entrySet()) {
            int key = structParentsIntegers.get(entry.getKey());
            nums.add(COSInteger.construct(key));
            COSObject array = COSArray.construct();
            for (COSObject structParent : entry.getValue()) {
                array.add(structParent);
            }
            nums.add(array);
            if (key >= nextKey) nextKey = key + 1;
        }
        // Add single-entry annotations (Link annotations) to parent tree
        for (Map.Entry<Integer, COSObject> entry : annotationStructParents.entrySet()) {
            nums.add(COSInteger.construct(entry.getKey()));
            nums.add(entry.getValue());
            if (entry.getKey() >= nextKey) nextKey = entry.getKey() + 1;
        }
        structTreeRoot.setKey(ASAtom.PARENT_TREE_NEXT_KEY, COSInteger.construct(nextKey));
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


    public static COSObject createStructureTreeElements(List<List<IObject>> contents, COSObject structTreeRoot, COSDocument cosDocument) {
        COSObject seDocument = addStructElement(structTreeRoot, cosDocument, TaggedPDFConstants.DOCUMENT, null);
        Map<SemanticHeading, Integer> normalizedLevels = buildNormalizedHeadingLevels(contents);
        // Flatten all top-level content into a single ordered list for caption association.
        List<IObject> flat = new ArrayList<>();
        for (List<IObject> pageContents : contents) {
            flat.addAll(pageContents);
        }
        // Pre-compute which Caption maps to which float (Table/Figure) and whether it's
        // a pre-caption (goes first) or post-caption (goes last).
        // PDF/UA-2 §8.2.5.27: Caption must be first or last child of its parent.
        // §Table5: Document must not directly contain Caption.
        // Map each Caption index to its nearest adjacent float (Table/Figure) index.
        // Captions will be attached as last child of their float parent regardless of
        // source order, satisfying §8.2.5.27 (first or last child).
        Map<Integer, Integer> captionToFloat = new HashMap<>();  // caption index → float index
        for (int i = 0; i < flat.size(); i++) {
            if (!(flat.get(i) instanceof SemanticCaption)) continue;
            // Look ahead for next Table/Figure (within 2 items, skipping other captions)
            for (int j = i + 1; j < flat.size() && j <= i + 2; j++) {
                IObject next = flat.get(j);
                if (isFloatElement(next)) { captionToFloat.put(i, j); break; }
                if (!(next instanceof SemanticCaption)) break;
            }
            if (captionToFloat.containsKey(i)) continue;
            // Look behind for previous Table/Figure
            for (int j = i - 1; j >= 0 && j >= i - 2; j--) {
                IObject prev = flat.get(j);
                if (isFloatElement(prev)) { captionToFloat.put(i, j); break; }
                if (!(prev instanceof SemanticCaption)) break;
            }
        }
        // Build float index → struct element map (created on demand during iteration)
        Map<Integer, COSObject> floatStructElems = new HashMap<>();
        // First pass: create all non-Caption elements; defer Captions
        for (int i = 0; i < flat.size(); i++) {
            IObject content = flat.get(i);
            if (content instanceof SemanticCaption) {
                continue; // deferred — handled after its float is created
            }
            COSObject elem;
            if (content instanceof SemanticHeading) {
                elem = null;
                createHeadingStructElem((SemanticHeading) content, seDocument, cosDocument,
                        normalizedLevels.get(content));
            } else if (content instanceof ImageChunk) {
                elem = createFigureStructElemReturning((ImageChunk) content, seDocument, cosDocument);
                floatStructElems.put(i, elem);
            } else if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                if (table.isTextBlock()) {
                    createPartStructElemForTextBlock(table, seDocument, cosDocument);
                    elem = null;
                } else if (!table.isOneCellTable()) {
                    elem = createTableStructElemReturning(table, seDocument, cosDocument);
                    floatStructElems.put(i, elem);
                } else {
                    elem = null;
                }
            } else {
                createStructElem(content, seDocument, cosDocument);
                elem = null;
            }
        }
        // Second pass: attach Captions to their float parent
        for (int i = 0; i < flat.size(); i++) {
            IObject content = flat.get(i);
            if (!(content instanceof SemanticCaption)) continue;
            Integer floatIdx = captionToFloat.get(i);
            COSObject floatElem = floatIdx != null ? floatStructElems.get(floatIdx) : null;
            if (floatElem != null) {
                createCaptionStructElem((SemanticCaption) content, floatElem, cosDocument);
            } else {
                // No adjacent float found — add directly to Document as fallback
                createCaptionStructElem((SemanticCaption) content, seDocument, cosDocument);
            }
        }
        return seDocument;
    }

    private static boolean isFloatElement(IObject obj) {
        if (obj instanceof ImageChunk) return true;
        if (obj instanceof TableBorder) {
            TableBorder t = (TableBorder) obj;
            return !t.isTextBlock() && !t.isOneCellTable();
        }
        return false;
    }

    /**
     * Normalizes heading levels across the document so that:
     * - The first heading is always H1.
     * - A heading may not skip levels going down (e.g., H1→H3 becomes H1→H2).
     * - A heading may jump back up freely (e.g., H3→H1 is fine).
     * This satisfies PDF/UA-1 §7.4.2 (strict descending sequence, no skipping).
     */
    private static Map<SemanticHeading, Integer> buildNormalizedHeadingLevels(List<List<IObject>> contents) {
        // Collect headings in document order
        List<SemanticHeading> headings = new ArrayList<>();
        for (List<IObject> page : contents) {
            for (IObject obj : page) {
                if (obj instanceof SemanticHeading) {
                    headings.add((SemanticHeading) obj);
                }
            }
        }
        Map<SemanticHeading, Integer> result = new IdentityHashMap<>();
        if (headings.isEmpty()) {
            return result;
        }
        // Two-pass: first map original levels to a dense 1-based sequence,
        // then assign normalized levels avoiding skips.
        int currentNormalized = 1;
        int prevOriginal = headings.get(0).getHeadingLevel();
        result.put(headings.get(0), 1);
        for (int i = 1; i < headings.size(); i++) {
            int orig = headings.get(i).getHeadingLevel();
            if (orig > prevOriginal) {
                // Going deeper — allow only one step at a time
                currentNormalized = Math.min(currentNormalized + 1, 6);
            } else if (orig < prevOriginal) {
                // Going back up — allow freely, but don't go below 1
                currentNormalized = Math.max(currentNormalized - (prevOriginal - orig), 1);
            }
            // else same level — keep currentNormalized
            result.put(headings.get(i), currentNormalized);
            prevOriginal = orig;
        }
        return result;
    }

    private static void createLinkAnnotationStructElements(PDDocument document, COSDocument cosDocument, COSObject seDocument) {
        List<PDPage> pages = document.getPages();
        // Annotation StructParent integers must start after all page StructParents.
        // structParentsIntegers is populated by updatePages() which runs before this method.
        int annotStructParentKey = structParentsIntegers.isEmpty() ? 0
                : structParentsIntegers.values().stream().mapToInt(Integer::intValue).max().getAsInt() + 1;
        for (int pageNum = 0; pageNum < pages.size(); pageNum++) {
            PDPage page = pages.get(pageNum);
            List<PDAnnotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            boolean pageChanged = false;
            for (PDAnnotation annotation : annotations) {
                if (!ASAtom.getASAtom("Link").equals(annotation.getSubtype())) continue;
                COSObject annotObj = annotation.getObject();
                if (annotObj == null || annotObj.empty()) continue;
                // For indirect annotations, resolve the canonical COSObject via its key
                // so that setKey writes go to the same instance that the writer will flush.
                org.verapdf.cos.COSKey cosKey = annotObj.getObjectKey();
                if (cosKey != null) {
                    COSObject canonical = cosDocument.getObject(cosKey);
                    if (canonical != null && !canonical.empty()) {
                        annotObj = canonical;
                    }
                }
                // Get URI from action if available
                String uriString = null;
                try {
                    PDAction action = annotation.getA();
                    if (action != null && action.getObject() != null && !action.getObject().empty()
                            && ASAtom.getASAtom("URI").equals(action.getSubtype())) {
                        COSObject uriObj = action.getObject().getKey(ASAtom.URI);
                        if (uriObj != null && !uriObj.empty()) {
                            uriString = uriObj.getString();
                        }
                    }
                } catch (Exception e) {
                    // ignore — URI not critical
                }
                // Create Link struct element
                COSObject linkElem = addStructElement(seDocument, cosDocument, TaggedPDFConstants.LINK, pageNum);
                // Set Alt text to URI or "Link"
                String altText = uriString != null ? uriString : "Link";
                linkElem.setKey(ASAtom.ALT, COSString.construct(altText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                // Assign StructParent integer to annotation and register in parent tree
                int structParentInt = annotStructParentKey++;
                annotObj.setKey(ASAtom.STRUCT_PARENT, COSInteger.construct(structParentInt));
                // Set Contents on annotation if absent.
                if (annotation.getContents() == null || annotation.getContents().isEmpty()) {
                    annotObj.setKey(ASAtom.CONTENTS,
                        COSString.construct(altText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                annotationStructParents.put(structParentInt, linkElem);
                cosDocument.addChangedObject(annotObj);
                pageChanged = true;
                // Create OBJR pointing to the annotation
                COSObject objr = COSDictionary.construct();
                objr.setKey(ASAtom.TYPE, COSName.construct(ASAtom.getASAtom("OBJR")));
                objr.setKey(ASAtom.OBJ, annotObj);
                objr.setKey(ASAtom.PG, page.getObject());
                COSObject kArray = COSArray.construct();
                kArray.add(objr);
                linkElem.setKey(ASAtom.K, kArray);
                cosDocument.addObject(linkElem);
            }
            // Flush the Annots array (may be an indirect object separate from the page)
            // so that direct-object annotation dicts inside it (StructParent + Contents) are saved.
            if (pageChanged) {
                COSObject annotsObj = page.getKey(ASAtom.ANNOTS);
                if (annotsObj != null && !annotsObj.empty()) {
                    cosDocument.addChangedObject(annotsObj);
                }
                cosDocument.addChangedObject(page.getObject());
            }
        }
    }

    private static void createStructElem(IObject object, COSObject parentStructElem, COSDocument cosDocument) {
        if (object instanceof SemanticHeading) {
            // Fallback: heading inside a nested context (list/table) — use original level
            createHeadingStructElem((SemanticHeading) object, parentStructElem, cosDocument,
                    ((SemanticHeading) object).getHeadingLevel());
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
        } else if (object instanceof SemanticFormula) {
            createFormulaStructElem((SemanticFormula) object, parentStructElem, cosDocument);
        } else if (object instanceof ImageChunk) {
            createFigureStructElem((ImageChunk) object, parentStructElem, cosDocument);
        }
    }

    private static void createHeadingStructElem(SemanticHeading heading, COSObject parent, COSDocument cosDocument,
                                                int normalizedLevel) {
        // Use the normalized level (1–6) so that:
        // - PDF/UA-1 §7.4.4: H is the only child of its parent (satisfied by H1-H6)
        // - PDF/UA-1 §7.4.2: heading levels do not skip (satisfied by normalization)
        COSObject headingObject = addStructElement(parent, cosDocument,
            TaggedPDFConstants.H + normalizedLevel,
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
        createFigureStructElemReturning(image, parent, cosDocument);
    }

    // imageChunkCounter is per-call; tracked via the figureObject index across a document
    private static int imageChunkFigureCounter = 0;

    private static COSObject createFigureStructElemReturning(ImageChunk image, COSObject parent, COSDocument cosDocument) {
        COSObject figureObject = addStructElement(parent, cosDocument, TaggedPDFConstants.FIGURE, image.getPageNumber());
        double[] bbox = {image.getLeftX(), image.getBottomY(), image.getRightX(), image.getTopY()};
        addAttributeToStructElem(figureObject, ASAtom.LAYOUT, ASAtom.BBOX, COSArray.construct(4, bbox));
        // Use enriched description if available, otherwise fallback "image N"
        String altText = (image instanceof EnrichedImageChunk && ((EnrichedImageChunk) image).hasDescription())
                ? ((EnrichedImageChunk) image).sanitizeDescription()
                : "image " + (++imageChunkFigureCounter);
        figureObject.setKey(ASAtom.ALT,
                COSString.construct(altText.getBytes(StandardCharsets.UTF_16), false));
        cosDocument.addChangedObject(figureObject);
        processImageNode(image, figureObject);
        return figureObject;
    }

    private static void createFormulaStructElem(SemanticFormula formula, COSObject parent, COSDocument cosDocument) {
        COSObject formulaObject = addStructElement(parent, cosDocument, "Formula", formula.getPageNumber());
        double[] bbox = {formula.getLeftX(), formula.getBottomY(), formula.getRightX(), formula.getTopY()};
        addAttributeToStructElem(formulaObject, ASAtom.LAYOUT, ASAtom.BBOX, COSArray.construct(4, bbox));
        String altText = formula.getLatex().isEmpty() ? "formula" : formula.getLatex();
        formulaObject.setKey(ASAtom.ALT,
                COSString.construct(altText.getBytes(StandardCharsets.UTF_16), false));
        cosDocument.addChangedObject(formulaObject);
        addMcidChildren(formula.getStreamInfos(), formula.getPageNumber(), formulaObject);
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
        createTableStructElemReturning(table, parent, cosDocument);
    }

    private static COSObject createTableStructElemReturning(TableBorder table, COSObject parent, COSDocument cosDocument) {
        COSObject tableObject = addStructElement(parent, cosDocument, TaggedPDFConstants.TABLE, table.getPageNumber());
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            COSObject rowObject = addStructElement(tableObject, cosDocument, TaggedPDFConstants.TR, row.getPageNumber());
            boolean isHeaderRow = (rowNumber == 0);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    String cellTag = isHeaderRow ? "TH" : TaggedPDFConstants.TD;
                    COSObject cellObject = addStructElement(rowObject, cosDocument, cellTag, cell.getPageNumber());
                    if (isHeaderRow) {
                        addAttributeToStructElem(cellObject, ASAtom.TABLE,
                            ASAtom.getASAtom("Scope"), COSName.construct(ASAtom.getASAtom("Column")));
                    }
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
        return tableObject;
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
                if (pdxObject != null) {
                    COSObject mcrDictionary = COSDictionary.construct();
                    mcrDictionary.setKey(ASAtom.TYPE, COSName.construct(ASAtom.MCR));
                    mcrDictionary.setKey(ASAtom.MCID, mcidObject);
                    mcrDictionary.setKey(ASAtom.STM, pdxObject.getObject());
                    array.add(mcrDictionary);
                } else {
                    array.add(mcidObject);
                }
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
