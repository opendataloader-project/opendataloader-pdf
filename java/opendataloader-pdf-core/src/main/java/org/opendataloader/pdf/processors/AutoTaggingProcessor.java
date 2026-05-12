package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.autotagging.OperatorStreamKey;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticFootnote;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASMemoryInStream;
import org.verapdf.cos.*;

import org.verapdf.gf.model.factory.chunks.GraphicsState;
import org.verapdf.gf.model.impl.operator.textshow.PUAHelper;
import org.verapdf.gf.model.impl.sa.util.ResourceHandler;
import org.verapdf.pd.*;
import org.verapdf.pd.actions.PDAction;
import org.verapdf.pd.form.PDAcroForm;
import org.verapdf.pd.form.PDFormField;
import org.verapdf.pd.images.PDXObject;
import org.verapdf.tools.StaticResources;
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
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

    private static final Map<OperatorStreamKey, Map<Integer, Set<StreamInfo>>> operatorIndexesToStreamInfosMap = new LinkedHashMap<>();
    private static final Map<OperatorStreamKey, List<COSObject>> structParents = new LinkedHashMap<>();
    private static final Map<OperatorStreamKey, Integer> structParentsIntegers = new LinkedHashMap<>();
    // annotation StructParent entries: int key -> single struct element (Link)
    private static final Map<Integer, COSObject> annotationStructParents = new HashMap<>();
    // First created struct element per page, used to rewrite page destinations to structure destinations.
    private static final Map<Integer, COSObject> pageNumberToFirstStructElement = new HashMap<>();
    private static final String FORMULA_REPLACEMENT_TEXT = "formula";
    private static final String IMAGE_REPLACEMENT_TEXT = "image ";
    private static final String ANNOTATION_REPLACEMENT_TEXT = "Annotation";
    // Namespace for PDF 2.0-only structure types (FENote, etc.), created in createStructTreeRoot.
    private static COSObject pdf2_0Namespace;
    // Caption elements keyed by their linked content ID (Raman's approach from #377)
    private static final Map<Long, SemanticCaption> structElementIdToCaptionMap = new HashMap<>();
    private static boolean isPDF2_0 = false;
    private static final Map<BoundingBox, PDAnnotation> annotationBBoxesMap = new LinkedHashMap<>();
    private static int currentStructParent = 0;
    private static final int MAX_TOKENS_PER_STREAM = 100_000;
    // imageChunkCounter is per-call; tracked via the figureObject index across a document
    private static int imageChunkFigureCounter = 0;

    /**
     * Tag a PDF document in-memory without saving to disk.
     * Adds structure tree, marked content references, and parent tree to the document.
     *
     * @param document  the PDDocument to tag (modified in place)
     * @param contents  extracted content by page
     */
    public static synchronized void tagDocument(PDDocument document, List<List<IObject>> contents, Float pdfVersion) throws IOException {
        operatorIndexesToStreamInfosMap.clear();
        structParents.clear();
        structParentsIntegers.clear();
        annotationStructParents.clear();
        pageNumberToFirstStructElement.clear();
        structElementIdToCaptionMap.clear();
        annotationBBoxesMap.clear();
        currentStructParent = 0;
        imageChunkFigureCounter = 0;
        isPDF2_0 = pdfVersion != null ? pdfVersion == 2.0F : document.getVersion() == 2.0F;
        COSDocument cosDocument = document.getDocument();
        PDCatalog catalog = document.getCatalog();
        COSObject structTreeRoot = createStructTreeRoot(catalog, cosDocument, document);
        createStructureTreeElements(document, contents, structTreeRoot, cosDocument);
        if (isPDF2_0) {
            updateDestinationsToStructureDestinations(document, catalog, cosDocument);
        }
        updatePages(document, cosDocument);
        createParentTree(cosDocument, structTreeRoot);
        cosDocument.getTrailer().removeKey(ASAtom.ENCRYPT);
    }

    /**
     * Tag a PDF document and save to disk. Existing behavior preserved.
     */
    public static synchronized void createTaggedPDF(File inputPDF, String outputFolder, PDDocument document, List<List<IObject>> contents) throws IOException {
        tagDocument(document, contents, null);
        String outputFileName = outputFolder + File.separator +
            inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_tagged.pdf";
        document.saveAs(outputFileName);
    }

    private static void updatePages(PDDocument document, COSDocument cosDocument) throws IOException {
        for (OperatorStreamKey operatorStreamKey : structParents.keySet()) {
            structParentsIntegers.put(operatorStreamKey, currentStructParent++);
        }
        List<PDPage> rawPages = document.getPages();
        for (int pageNumber = 0; pageNumber < rawPages.size(); pageNumber++) {
            PDPage page = rawPages.get(pageNumber);
            if (isPDF2_0) {
                updateAdditionalAction(page.getObject(), cosDocument, document);
            }
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
        // Only emit the PDF 2.0 namespace (pdf2/ssn) when the output is PDF 2.0.
        // The namespace gates PDF 2.0-only structure types like FENote (tagged
        // with an explicit /NS to satisfy PDF/UA-2 clause 8.2.4.1). Attaching
        // /Namespaces on a PDF 1.x file would produce invalid output.
        pdf2_0Namespace = null;
        if (isPDF2_0) {
            pdf2_0Namespace = COSDictionary.construct(ASAtom.NS, TaggedPDFConstants.PDF2_NAMESPACE);
            pdf2_0Namespace.setNameKey(ASAtom.TYPE, ASAtom.NAMESPACE);
            pdf2_0Namespace = COSIndirect.construct(pdf2_0Namespace, cosDocument);
            cosDocument.addObject(pdf2_0Namespace);
            COSObject namespaces = COSArray.construct();
            namespaces.add(pdf2_0Namespace);
            structTreeRoot.setKey(ASAtom.NAMESPACES, namespaces);
        }
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
        return addStructElement(parent, cosDocument, type, pageNumber, false);
    }

    private static COSObject addStructElement(COSObject parent, COSDocument cosDocument, String type, Integer pageNumber, boolean isFirstKid) {
        COSObject structElement = COSIndirect.construct(COSDictionary.construct(), cosDocument);
        COSObject k = parent.getKey(ASAtom.K);
        if (k.getType() == COSObjType.COS_ARRAY) {
            if (isFirstKid) {
                k.insert(0, structElement);
            } else {
                k.add(structElement);
            }
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
            pageNumberToFirstStructElement.putIfAbsent(pageNumber, structElement);
        }
        cosDocument.addObject(structElement);
        return structElement;
    }


    public static COSObject createStructureTreeElements(PDDocument document, List<List<IObject>> contents,
                                                        COSObject structTreeRoot, COSDocument cosDocument) {
        COSObject seDocument = addStructElement(structTreeRoot, cosDocument, TaggedPDFConstants.DOCUMENT, null);
        Map<SemanticHeading, Integer> normalizedLevels = buildNormalizedHeadingLevels(contents);
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            processAnnotations(document, cosDocument, pageNumber);
            List<IObject> pageContents = contents.get(pageNumber);
            addKids(pageContents, seDocument, cosDocument, normalizedLevels);
            processAnnotations(cosDocument, seDocument, pageNumber);
        }
        return seDocument;
    }

    /**
     * Adds child struct elements, collecting Captions and attaching them to their
     * linked float (Figure/Table/List) via addCaptionIfPresent().
     * Based on Raman Kakhnovich's approach from origin/auto_tagging #377.
     */
    private static void addKids(List<IObject> contents, COSObject parentStructElem, COSDocument cosDocument,
                                 Map<SemanticHeading, Integer> normalizedLevels) {
        // First pass: collect Caption → linkedContentId mappings
        for (IObject content : contents) {
            if (content instanceof SemanticCaption) {
                structElementIdToCaptionMap.put(
                    ((SemanticCaption) content).getLinkedContentId(), (SemanticCaption) content);
            }
        }
        // Second pass: create struct elements (skipping Captions — they are attached by addCaptionIfPresent)
        for (IObject content : contents) {
            if (content instanceof SemanticCaption) {
                continue;
            }
            if (content instanceof SemanticHeading && normalizedLevels != null) {
                createHeadingStructElem((SemanticHeading) content, parentStructElem, cosDocument,
                    normalizedLevels.get(content));
            } else {
                createStructElem(content, parentStructElem, cosDocument);
            }
        }
    }

    /** Overload for nested contexts (list items, table cells) where heading normalization is not applicable. */
    private static void addKids(List<IObject> contents, COSObject parentStructElem, COSDocument cosDocument) {
        addKids(contents, parentStructElem, cosDocument, null);
    }

    /**
     * If a Caption is linked to this content element, attach it as first or last child
     * of the struct element based on spatial position.
     */
    private static void addCaptionIfPresent(IObject content, COSObject linkedObject, COSDocument cosDocument) {
        Long linkedContentId = content.getRecognizedStructureId();
        if (linkedContentId != null && structElementIdToCaptionMap.containsKey(linkedContentId)) {
            SemanticCaption caption = structElementIdToCaptionMap.get(linkedContentId);
            boolean isFirst = isCaptionFirstChild(caption.getBoundingBox(), content.getBoundingBox());
            createCaptionStructElem(caption, linkedObject, cosDocument, isFirst);
        }
    }

    /**
     * Determines if the caption should be the first child (above/before) or last child
     * (below/after) of its parent struct element.
     */
    private static boolean isCaptionFirstChild(BoundingBox caption, BoundingBox parent) {
        if (caption == null || parent == null) return true;
        if (caption.getCenterY() > parent.getTopY()) {
            return true;
        } else if (caption.getCenterY() < parent.getBottomY()) {
            return false;
        } else {
            return caption.getCenterX() < parent.getCenterX();
        }
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

    private static boolean needToAddAnnotationToStructTree(PDAnnotation annotation, PDPage page, BoundingBox boundingBox) {
        //PDF/UA-1 rule 7.18.2-1 / PDF/UA-2 rule 8.9.2.4.15-1
        if (ASAtom.TRAP_NET.equals(annotation.getSubtype())) {
            return false;
        }
        if (isPDF2_0) {
            Long f = annotation.getIntegerKey(ASAtom.F);
            //PDF/UA-2 rules 8.9.2.2-1, 8.9.2.2-2
            if (f != null && ((f & 1) != 0 || ((f & 32) != 0 && (f & 256) == 0))) {
                return false;
            }
            ASAtom subtype = annotation.getSubtype();
            //PDF/UA-2 8.9.2.4.11-1, 8.9.2.4.11-2, 8.9.2.4.9-1
            if (ASAtom.SOUND.equals(subtype) || ASAtom.MOVIE.equals(subtype) || ASAtom.POPUP.equals(subtype)) {
                return false;
            }
            //PDF/UA-2 rules 8.10.1-1, 8.9.2.4.13-1, 8.9.2.4.16-1, 8.9.2.3-1, 8.2.5.20-1
            return (ASAtom.WIDGET.equals(subtype) && boundingBox.getHeight() != 0 && boundingBox.getWidth() != 0)
                || ASAtom.WATERMARK.equals(subtype) || annotation.isMarkup() || ASAtom.LINK.equals(subtype);
        } else {
            //PDF/UA-1 rules 7.18.1-1, 7.18.4-1, 7.18.5-1
            return !ASAtom.PRINTER_MARK.equals(annotation.getSubtype()) &&
                !PDAnnotation.isOutsideCropBox(page, annotation) && PDAnnotation.isVisibleAnnotation(annotation);
        }
    }

    private static void setAnnotationContents(PDAnnotation annotation, COSObject annotObj) {
        String existingContents = annotation.getContents();
        // Preserve the annotation's existing Contents when present
        if (existingContents == null || existingContents.isEmpty()) {
            // Prefer any existing Contents authored on the annotation (accessibility
            // text the author already wrote), otherwise fall back to URI (for Link only),
            // then to "Annotation".
            String contentsText = null;
            // Get URI from action if available
            if (ASAtom.LINK.equals(annotation.getSubtype())) {
                PDAction action = annotation.getA();
                if (action != null && action.getObject() != null && ASAtom.URI.equals(action.getSubtype())) {
                    contentsText = action.getStringKey(ASAtom.URI);
                }
            }
            //TODO Use AI to generate descriptions
            setStringEntry(contentsText, annotObj, ANNOTATION_REPLACEMENT_TEXT, ASAtom.CONTENTS, false);
        } else {
            if (PUAHelper.containPUA(existingContents)) {
                setStringEntry(existingContents, annotObj, ANNOTATION_REPLACEMENT_TEXT, ASAtom.CONTENTS, false);
            }
        }
    }

    //PDF/UA-2 rule 8.4.3-3
    private static void setStringEntry(String contents, COSObject object, String replacementText, ASAtom key,  boolean useFigureCounter) {
        contents = stripPuaCodePoints(contents);
        if (contents == null || contents.isEmpty()) {
            if (useFigureCounter) {
                contents = replacementText + (++imageChunkFigureCounter);
            } else {
                contents = replacementText;
            }
        }
        COSObject textObject = COSString.construct(
            contents.getBytes(StandardCharsets.UTF_16), true);
        object.setKey(key, textObject);
    }

    private static void processAnnotations(PDDocument document, COSDocument cosDocument, int pageNumber) {
        PDPage page = document.getPages().get(pageNumber);
        List<PDAnnotation> annotations = page.getAnnotations();
        if (annotations == null) return;
        boolean pageChanged = false;
        for (PDAnnotation annotation : annotations) {
            COSObject annotObj = annotation.getObject();
            if (annotObj == null || annotObj.empty()) continue;

            BoundingBox boundingBox = new BoundingBox(page.getPageNumber(), annotation.getRect());
            if (needToAddAnnotationToStructTree(annotation, page, boundingBox)) {
                annotationBBoxesMap.put(boundingBox, annotation);
                setAnnotationContents(annotation, annotObj);
                // Assign StructParent integer to annotation and register in parent tree
                int structParentInt = currentStructParent++;
                annotObj.setKey(ASAtom.STRUCT_PARENT, COSInteger.construct(structParentInt));
                cosDocument.addChangedObject(annotObj);
                pageChanged = true;
            } else {
                if (annotObj.knownKey(ASAtom.STRUCT_PARENT)) {
                    annotObj.removeKey(ASAtom.STRUCT_PARENT);
                    cosDocument.addChangedObject(annotObj);
                    pageChanged = true;
                }
            }
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

    private static void processAnnotations(COSDocument cosDocument, COSObject seDocument, int pageNumber) {
        for (PDAnnotation annotation : annotationBBoxesMap.values()) {
            createAnnotationStructElem(cosDocument, seDocument, annotation, null, pageNumber);
        }
        annotationBBoxesMap.clear();
    }

    private static void createAnnotationStructElem(COSDocument cosDocument, COSObject parent, PDAnnotation annotation,
                                                   List<StreamInfo> streamInfos, int pageNumber) {

        String tag;
        if (ASAtom.LINK.equals(annotation.getSubtype())) {
            tag = TaggedPDFConstants.LINK;
        } else if (ASAtom.WIDGET.equals(annotation.getSubtype())) {
            tag = TaggedPDFConstants.FORM;
        } else {
            tag = TaggedPDFConstants.ANNOT;
        }
        COSObject structElement = addStructElement(parent, cosDocument, tag, pageNumber);

        // PDF/UA-2 rule 8.9.4.2-1 requires the annotation's Contents and the enclosing
        // struct element's Alt to be identical when both are present.
        COSObject contents = annotation.getKey(ASAtom.CONTENTS);
        if (contents != null && !contents.empty() && contents.getType() == COSObjType.COS_STRING) {
            structElement.setKey(ASAtom.ALT, contents);
        }
        annotationStructParents.put(annotation.getIntegerKey(ASAtom.STRUCT_PARENT).intValue(), structElement);
        // Create OBJR pointing to the annotation
        COSObject objr = COSDictionary.construct();
        objr.setKey(ASAtom.TYPE, COSName.construct(ASAtom.OBJR));
        objr.setKey(ASAtom.OBJ, annotation.getObject());
        objr.setKey(ASAtom.PG, cosDocument.getPDDocument().getPages().get(pageNumber).getObject());
        COSObject kArray = COSArray.construct();
        kArray.add(objr);
        structElement.setKey(ASAtom.K, kArray);
        if (streamInfos != null) {
            addMcidChildren(streamInfos, pageNumber, structElement);
        }
    }

    private static void updateDestinationsToStructureDestinations(PDDocument document, PDCatalog catalog, COSDocument cosDocument) {
        for (PDPage page: document.getPages()) {
            List<PDAnnotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            for (PDAnnotation annotation : annotations) {
                COSObject annotObj = annotation.getObject();
                if (annotObj == null || annotObj.empty()) continue;
                rewriteDestinationToStructDestinationInAction(annotObj, document, cosDocument);
                updateAdditionalAction(annotObj, cosDocument, document);
                if (!ASAtom.LINK.equals(annotation.getSubtype())) continue;
                rewriteDestinationToStructDestinationInLinkAnnotation(annotObj, document);
            }
        }
        updateOutlines(cosDocument, document);
        updateOpenAction(cosDocument, catalog, document);
        updateAdditionalAction(catalog.getObject(), cosDocument, document);
        updateAcroForm(document, cosDocument);
    }

    /**
     * Make a Link annotation's destination compliant with PDF/UA-2 clause 8.8 (all internal
     * destinations must be structure destinations).
     *
     * <p>Behaviour differs between annotation {@code /Dest} and action {@code /A /D}:
     * <ul>
     *   <li>For annotation {@code /Dest}: veraPDF checks the array's first element is a struct
     *       element (via {@code at(0).knownKey(S)}), so rewrite the first slot to a struct elem ref.
     *   <li>For a GoTo action: veraPDF first checks {@code /SD} on the action dict itself, so add
     *       an {@code /SD [structElem /Fit]} entry alongside (or instead of) the existing {@code /D}.
     * </ul>
     */
    private static void rewriteDestinationToStructDestinationInLinkAnnotation(COSObject annotObj, PDDocument document) {
        COSObject dest = annotObj.getKey(ASAtom.DEST);
        if (dest != null && !dest.empty()) {
            COSObject structDestArray = buildStructDestArray(dest, document);
            if (structDestArray != null) {
                annotObj.setKey(ASAtom.DEST, structDestArray);
            } else {
                annotObj.removeKey(ASAtom.DEST);
            }
            document.getDocument().addChangedObject(annotObj);
        }
    }

    private static void rewriteDestinationToStructDestinationInAction(COSObject object, PDDocument document, COSDocument cosDocument) {
        rewriteDestinationToStructDestinationInAction(object, document, cosDocument, ASAtom.A);
    }

    private static void rewriteDestinationToStructDestinationInAction(COSObject object, PDDocument document, COSDocument cosDocument, ASAtom key) {
        COSObject action = object.getKey(key);
        if (action == null || action.empty() || action.getType() != COSObjType.COS_DICT) {
            return;
        }
        if (!ASAtom.GO_TO.equals(action.getNameKey(ASAtom.S))) {
            return;
        }
        COSObject d = action.getKey(ASAtom.D);
        COSObject structDestArray = buildStructDestArray(d, document);
        if (structDestArray != null) {
            action.setKey(ASAtom.SD, structDestArray);
            cosDocument.addChangedObject(action);
        } else {
            object.removeKey(key);
            cosDocument.addChangedObject(object);
        }
    }

    private static void updateAdditionalAction(COSObject object, COSDocument cosDocument, PDDocument document) {
        if (object.knownKey(ASAtom.AA)) {
            COSObject aaEntry = object.getKey(ASAtom.AA);
            if (aaEntry.getType() == COSObjType.COS_DICT) {
                COSDictionary dictionary = (COSDictionary) aaEntry.getDirectBase();
                for (ASAtom key : new ArrayList<>(dictionary.getKeySet())) {
                    rewriteDestinationToStructDestinationInAction(aaEntry, document, cosDocument, key);
                }
            }
        }
    }

    private static void updateOpenAction(COSDocument cosDocument, PDCatalog catalog, PDDocument document) {
        if (catalog.knownKey(ASAtom.OPEN_ACTION)) {
            COSObject openAction = catalog.getKey(ASAtom.OPEN_ACTION);
            if (openAction.getType() == COSObjType.COS_ARRAY) {
                COSObject sd = buildStructDestArray(openAction, document);
                if (sd != null) {
                    catalog.setKey(ASAtom.OPEN_ACTION, sd);
                } else {
                    catalog.removeKey(ASAtom.OPEN_ACTION);
                }
                cosDocument.addChangedObject(catalog.getObject());
                return;
            }
            rewriteDestinationToStructDestinationInAction(catalog.getObject(), document,  cosDocument,  ASAtom.OPEN_ACTION);
        }
    }

    private static void updateAcroForm(PDDocument document, COSDocument cosDocument) {
        PDAcroForm acroForm = document.getAcroForm();
        if (acroForm != null) {
            Deque<PDFormField> deque = new ArrayDeque<>(acroForm.getFields());
            while (!deque.isEmpty()) {
                PDFormField field = deque.poll();
                updateAdditionalAction(field.getObject(), cosDocument, document);
                deque.addAll(field.getChildFormFields());
            }
        }
    }

    /**
     * Build a {@code [structElem /Fit]} array suitable as a structure destination. Uses the
     * target page from an array-form page destination when available. Falls back to the
     * annotation's own page only when the destination is entirely absent — a present-but-
     * unresolvable destination (named, non-array, or an unmatched page) returns {@code null}
     * so the caller leaves the original destination untouched rather than silently
     * redirecting a cross-page link to the annotation's own page.
     */
    private static COSObject buildStructDestArray(COSObject originalDest, PDDocument document) {
        COSObject target = null;
        boolean destIsPresent = originalDest != null && !originalDest.empty();
        COSObject page;
        if (destIsPresent) {
            page = PDAnnotation.getPageFromDestination(originalDest, ASAtom.D);
            if (page != null && !page.empty() && page.getType() == COSObjType.COS_DICT
                    && ASAtom.PAGE.equals(page.getNameKey(ASAtom.TYPE))) {
                List<PDPage> pages = document.getPages();
                for (int i = 0; i < pages.size(); i++) {
                    if (pages.get(i).getObject().getObjectKey().equals(page.getObjectKey())) {
                        target = pageNumberToFirstStructElement.get(i);
                        break;
                    }
                }
            }
        }

        if (target == null) {
            return null;
        }
        COSObject arr = COSArray.construct();
        arr.add(target);
        arr.add(COSName.construct(ASAtom.getASAtom("Fit")));
        return arr;
    }

    private static void updateOutlines(COSDocument cosDocument, PDDocument document) {
        PDOutlineDictionary dictionary = document.getOutlines();
        if (dictionary == null) {
            return;
        }
        updateOutlineItem(dictionary.getFirst(), document, cosDocument);
    }

    private static void updateOutlineItem(PDOutlineItem current, PDDocument document, COSDocument cosDocument) {
        while (current != null) {
            COSObject currentObject = current.getObject();
            rewriteDestinationToStructDestinationInLinkAnnotation(currentObject, document);
            rewriteDestinationToStructDestinationInAction(currentObject, document, cosDocument);
            updateOutlineItem(current.getFirst(), document, cosDocument);
            current = current.getNext();
        }
    }

    private static void createStructElem(IObject object, COSObject parentStructElem, COSDocument cosDocument) {
        if (object instanceof SemanticHeading) {
            // Fallback: heading inside a nested context (list/table) — use original level
            createHeadingStructElem((SemanticHeading) object, parentStructElem, cosDocument,
                    ((SemanticHeading) object).getHeadingLevel());
        } else if (object instanceof SemanticFootnote) {
            createFootnoteStructElem((SemanticFootnote) object, parentStructElem, cosDocument);
        } else if (object instanceof SemanticParagraph) {
            createParagraphStructElem((SemanticParagraph) object, parentStructElem, cosDocument);
        } else if (object instanceof PDFList) {
            createListStructElem((PDFList) object, parentStructElem, cosDocument);
        } else if (object instanceof TableBorder) {
            TableBorder table = (TableBorder) object;
            if (table.isTextBlock()) {
                createStructElemForTextBlock(table, parentStructElem, cosDocument);
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

    /**
     * Remove Unicode Private Use Area code points. PDF/UA-2 clause 8.4.3.3 forbids PUA chars in
     * /Alt entries. Iterates by code point so surrogate pairs for supplementary PUA are handled.
     */
    public static String stripPuaCodePoints(String in) {
        if (in == null || in.isEmpty()) return in;
        StringBuilder sb = new StringBuilder(in.length());
        int i = 0;
        while (i < in.length()) {
            int cp = in.codePointAt(i);
            boolean isPua = (cp >= 0xE000 && cp <= 0xF8FF)
                    || (cp >= 0xF0000 && cp <= 0xFFFFD)
                    || (cp >= 0x100000 && cp <= 0x10FFFD);
            if (!isPua) sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static void createFootnoteStructElem(SemanticFootnote footnote, COSObject parent, COSDocument cosDocument) {
        // FENote + /NS + /NoteType is the PDF 2.0 path (PDF/UA-2 clause 8.2.4.1).
        // For PDF 1.x output, fall back to the Note standard structure type so the
        // result is valid for PDF 1.7 tagged-PDF consumers.
        boolean usePdf2Footnote = isPDF2_0 && pdf2_0Namespace != null;
        COSObject noteObject = addStructElement(parent, cosDocument,
                usePdf2Footnote ? TaggedPDFConstants.FENOTE : TaggedPDFConstants.NOTE,
                footnote.getPageNumber());
        if (usePdf2Footnote) {
            noteObject.setKey(ASAtom.NS, pdf2_0Namespace);
            noteObject.setKey(ASAtom.NOTE_TYPE, COSName.construct(ASAtom.getASAtom("Footnote")));
        }
        processTextNode(footnote, noteObject);
    }

    private static void createCaptionStructElem(SemanticCaption caption, COSObject parent, COSDocument cosDocument, boolean isFirstChild) {
        COSObject captionObject = addStructElement(parent, cosDocument, TaggedPDFConstants.CAPTION, caption.getPageNumber(), isFirstChild);
        processTextNode(caption, captionObject);
    }

    private static void createFigureStructElem(ImageChunk image, COSObject parent, COSDocument cosDocument) {
        createFigureStructElemReturning(image, parent, cosDocument);
    }

    private static COSObject createFigureStructElemReturning(ImageChunk image, COSObject parent, COSDocument cosDocument) {
        COSObject figureObject = addStructElement(parent, cosDocument, TaggedPDFConstants.FIGURE, image.getPageNumber());
        double[] bbox = {image.getLeftX(), image.getBottomY(), image.getRightX(), image.getTopY()};
        addAttributeToStructElem(figureObject, ASAtom.LAYOUT, ASAtom.BBOX, COSArray.construct(4, bbox));
        //PDF/UA-1 rule 7.3-1 / PDF/UA-2 rule 8.2.5.28.2-1
        // Use enriched description if available, otherwise fallback "image N"
        String altText = null;
        if (image instanceof EnrichedImageChunk) {
            altText = ((EnrichedImageChunk) image).sanitizeDescription();
        }
        // Write as hex string (isHex=true). UTF-16BE code units whose low byte is 0x5C (e.g. U+D55C "한")
        // would be misparsed as a backslash escape inside a PDF literal string, shifting all subsequent
        // bytes by one and producing PUA code points that fail PDF/UA-2 clause 8.4.3.3.
        setStringEntry(altText, figureObject, IMAGE_REPLACEMENT_TEXT, ASAtom.ALT, true);
        cosDocument.addChangedObject(figureObject);
        processImageNode(image, figureObject);
        addCaptionIfPresent(image, figureObject, cosDocument);
        return figureObject;
    }


    private static void createFormulaStructElem(SemanticFormula formula, COSObject parent, COSDocument cosDocument) {
        COSObject formulaObject = addStructElement(parent, cosDocument, TaggedPDFConstants.FORMULA, formula.getPageNumber());
        double[] bbox = {formula.getLeftX(), formula.getBottomY(), formula.getRightX(), formula.getTopY()};
        addAttributeToStructElem(formulaObject, ASAtom.LAYOUT, ASAtom.BBOX, COSArray.construct(4, bbox));
        //PDF/UA-1 rule 7.7-1
        String altText = formula.getLatex();
        setStringEntry(altText, formulaObject, FORMULA_REPLACEMENT_TEXT, ASAtom.ALT, false);
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
        ASAtom numbering = ListProcessor.getListNumbering(list.getNumberingStyle());
        // ListProcessor.getListNumbering returns null for unmapped styles. Fold
        // null into NONE so the hasLabel promotion and COSName.construct below
        // never receive null.
        if (numbering == null || numbering == ASAtom.NONE) {
            boolean hasLabel = false;
            for (ListItem item : list.getListItems()) {
                if (item.getLabelLength() > 0) { hasLabel = true; break; }
            }
            numbering = hasLabel ? ASAtom.ORDERED : ASAtom.NONE;
        }
        addAttributeToStructElem(listObject, ASAtom.LIST, ASAtom.LIST_NUMBERING, COSName.construct(numbering));

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
            addKids(listItem.getContents(), lBodyObject, cosDocument);
        }
        addCaptionIfPresent(list, listObject, cosDocument);
    }

    private static void createTableStructElem(TableBorder table, COSObject parent, COSDocument cosDocument) {
        createTableStructElemReturning(table, parent, cosDocument);
    }

    private static COSObject createTableStructElemReturning(TableBorder table, COSObject parent, COSDocument cosDocument) {
        COSObject tableObject = addStructElement(parent, cosDocument, TaggedPDFConstants.TABLE, table.getPageNumber());

        // Flat structure: Table > TR > TH/TD (no THead/TBody wrappers)
        // First row uses TH + Scope="Column" for header identification.
        // This is compatible with both Adobe Acrobat and veraPDF PDF/UA-2 validation.
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            addTableRow(table, rowNumber, tableObject, cosDocument, rowNumber == 0);
        }

        addCaptionIfPresent(table, tableObject, cosDocument);
        return tableObject;
    }

    private static void addTableRow(TableBorder table, int rowNumber, COSObject parent,
                                    COSDocument cosDocument, boolean isHeaderRow) {
        TableBorderRow row = table.getRow(rowNumber);
        COSObject rowObject = addStructElement(parent, cosDocument, TaggedPDFConstants.TR, row.getPageNumber());
        for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
            TableBorderCell cell = row.getCell(colNumber);
            if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                String cellTag = isHeaderRow ? TaggedPDFConstants.TH : TaggedPDFConstants.TD;
                COSObject cellObject = addStructElement(rowObject, cosDocument, cellTag, cell.getPageNumber());
                if (isHeaderRow) {
                    addAttributeToStructElem(cellObject, ASAtom.TABLE,
                        ASAtom.SCOPE, COSName.construct(ASAtom.getASAtom("Column")));
                }
                if (cell.getColSpan() != 1) {
                    addAttributeToStructElem(cellObject, ASAtom.TABLE, ASAtom.COL_SPAN, COSInteger.construct(cell.getColSpan()));
                }
                if (cell.getRowSpan() != 1) {
                    addAttributeToStructElem(cellObject, ASAtom.TABLE, ASAtom.ROW_SPAN, COSInteger.construct(cell.getRowSpan()));
                }
                addKids(cell.getContents(), cellObject, cosDocument);
            }
        }
    }

    private static void createStructElemForTextBlock(TableBorder table, COSObject parent, COSDocument cosDocument) {
        boolean useAside = isPDF2_0 && pdf2_0Namespace != null;
        COSObject partObject = addStructElement(parent, cosDocument, useAside ? TaggedPDFConstants.ASIDE : TaggedPDFConstants.ART, table.getPageNumber());
        if (useAside) {
            partObject.setKey(ASAtom.NS, pdf2_0Namespace);
        }
        TableBorderCell cell = table.getCell(0,0);
        addKids(cell.getContents(), partObject, cosDocument);
        addCaptionIfPresent(table, partObject, cosDocument);
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
                    Map.Entry<BoundingBox, PDAnnotation> entry = getAnnotationByBBox(textChunk.getBoundingBox());
                    if (entry != null) {
                        annotationBBoxesMap.remove(entry.getKey());
                        if (!streamInfos.isEmpty()) {
                            addMcidChildren(streamInfos, textNode.getPageNumber(), cosObject);
                            streamInfos.clear();
                        }
                        createAnnotationStructElem(StaticResources.getDocument().getDocument(), cosObject, entry.getValue(),
                            textChunk.getStreamInfos(), textNode.getPageNumber());
                    } else {
                        streamInfos.addAll(textChunk.getStreamInfos());
                    }
                }
            }
        }
        addMcidChildren(streamInfos, textNode.getPageNumber(), cosObject);
    }

    private static Map.Entry<BoundingBox, PDAnnotation> getAnnotationByBBox(BoundingBox boundingBox) {
        for (Map.Entry<BoundingBox, PDAnnotation> entry : annotationBBoxesMap.entrySet()) {
            if (entry.getKey().getIntersectionPercent(boundingBox) > 0.5d) {
                return entry;
            }
        }
        return null;
    }

    private static void processImageNode(ImageChunk imageChunk, COSObject cosObject) {
        addMcidChildren(imageChunk.getStreamInfos(), imageChunk.getPageNumber(), cosObject);
    }

    private static void addMcidChildren(List<StreamInfo> streamInfos, Integer pageNumber, COSObject parentStructElement) {
        COSObject kids = parentStructElement.getKey(ASAtom.K);
        if (kids.getType() != COSObjType.COS_ARRAY) {
            COSObject array = COSArray.construct();
            parentStructElement.setKey(ASAtom.K, array);
            kids = array;
        }
        if (streamInfos.isEmpty()) {
            return;
        }
        List<StreamInfo> streamInfoList = getMergedStreamInfos(streamInfos);
        for (StreamInfo streamInfo : streamInfoList) {
            OperatorStreamKey operatorStreamKey = new OperatorStreamKey(pageNumber, streamInfo.getXObjectName());
            List<COSObject> list = structParents.computeIfAbsent(operatorStreamKey, x -> new ArrayList<>());
            int mcid = list.size();
            COSObject mcidObject = COSInteger.construct(mcid);
            streamInfo.setMcid(mcid);
            operatorIndexesToStreamInfosMap.computeIfAbsent(operatorStreamKey, x -> new HashMap<>())
                .computeIfAbsent(streamInfo.getOperatorIndex(), x -> new TreeSet<>()).add(streamInfo);
            list.add(parentStructElement);
            if (streamInfo.getXObjectName() != null) {
                PDXObject pdxObject = StaticResources.getDocument().getPage(pageNumber).getResources()
                    .getXObject(ASAtom.getASAtom(streamInfo.getXObjectName()));
                if (pdxObject != null) {
                    COSObject mcrDictionary = COSDictionary.construct();
                    mcrDictionary.setKey(ASAtom.TYPE, COSName.construct(ASAtom.MCR));
                    mcrDictionary.setKey(ASAtom.MCID, mcidObject);
                    mcrDictionary.setKey(ASAtom.STM, pdxObject.getObject());
                    kids.add(mcrDictionary);
                } else {
                    kids.add(mcidObject);
                }
            } else {
                kids.add(mcidObject);
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
                previousInfo.getEndIndex() <= currentStreamInfo.getStartIndex()) {
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
