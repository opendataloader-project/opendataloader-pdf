package org.opendataloader.pdf.autotagging;

import org.opendataloader.pdf.processors.AutoTaggingProcessor;
import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASInputStream;
import org.verapdf.cos.*;
import org.verapdf.gf.model.factory.chunks.ChunkParser;
import org.verapdf.gf.model.factory.chunks.GraphicsState;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.parser.PDFStreamParser;
import org.verapdf.pd.PDContentStream;
import org.verapdf.pd.PDExtGState;
import org.verapdf.pd.PDResourcesHandler;
import org.verapdf.pd.images.PDXForm;
import org.verapdf.pd.images.PDXObject;
import org.verapdf.tools.StaticResources;
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ChunksWriter {

    private static final java.util.logging.Logger CHUNKS_LOGGER = java.util.logging.Logger.getLogger(ChunksWriter.class.getName());

    private final PDResourcesHandler resourceHandler;
    private final GraphicsState graphicsState;

    public ChunksWriter(GraphicsState inheritedGraphicState, PDResourcesHandler resourceHandler) {
        this.graphicsState = inheritedGraphicState.clone();
        this.resourceHandler = resourceHandler;
    }

    public static List<Object> getTokens(PDContentStream pdContentStream) {
        if (pdContentStream != null) {
            try {
                COSObject contentStream = pdContentStream.getContents();
                if (contentStream.getType() == COSObjType.COS_STREAM || contentStream.getType() == COSObjType.COS_ARRAY) {
                    try (ASInputStream opStream = contentStream.getDirectBase().getData(COSStream.FilterFlags.DECODE)) {
                        try (PDFStreamParser streamParser = new PDFStreamParser(opStream)) {
                            streamParser.parseTokens();
                            return streamParser.getTokens();
                        }
                    }
                }
            } catch (IOException e) {
                CHUNKS_LOGGER.warning("Failed to read content stream tokens: " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Path-construction operators (re/m/l/c/v/y/h) seen since the last paint or
     * clip operator, together with their operands.
     *
     * <p>A path is built by one or more construction operators and then realised
     * by a paint operator. Only the paint operator used to reach
     * {@link #processContentOperator}, so the BDC/EMC pair wrapped the paint
     * alone and every preceding construction operator stayed outside it -- the
     * shape was drawn but sat in no marked-content sequence, unreachable from
     * the structure tree. Buffering them here lets the whole path be emitted
     * inside its span. Charts are the visible casualty: their bars and gridlines
     * are construction operators almost entirely.
     */
    private final List<Object> pendingPath = new ArrayList<>();

    public List<Object> processTokens(List<Object> processTokens, OperatorStreamKey operatorStreamKey) throws IOException {
        Map<Integer, Set<StreamInfo>> operatorIndexesToStreamInfosMap = AutoTaggingProcessor.getOperatorIndexesToStreamInfosMap().get(operatorStreamKey);
        if (operatorIndexesToStreamInfosMap == null) {
            operatorIndexesToStreamInfosMap = Collections.emptyMap();
        }
        List<Object> result = new ArrayList<>();
        List<COSBase> arguments = new ArrayList<>();
        for (int index = 0; index < processTokens.size(); index++) {
            Object token = processTokens.get(index);
            if (token instanceof COSBase) {
                arguments.add((COSBase) token);
            } else if (token instanceof Operator) {
                processOperator(result, (Operator)token, arguments, index, operatorIndexesToStreamInfosMap, operatorStreamKey);
                arguments.clear();
            }
        }
        return result;
    }

    private void processOperator(List<Object> result, Operator rawOperator, List<COSBase> arguments, int operatorIndex,
                                 Map<Integer, Set<StreamInfo>> operatorIndexesToStreamInfosMap,
                                 OperatorStreamKey operatorStreamKey) throws IOException {
        String operatorName = rawOperator.getOperator();
        switch (operatorName) {
            case Operators.BMC:
            case Operators.EMC:
            case Operators.BDC:
                break;
            case Operators.DO:
                OperatorStreamKey xObjectOperatorStreamKey = new OperatorStreamKey(operatorStreamKey.getPageNumber(), arguments.get(0).getString());
                Integer xObjectStructParent = AutoTaggingProcessor.getStructParentsIntegers().get(xObjectOperatorStreamKey);
                if (AutoTaggingProcessor.getOperatorIndexesToStreamInfosMap().containsKey(xObjectOperatorStreamKey)
                        && xObjectStructParent != null) {
                    COSName xObjectName = getLastCOSName(arguments);
                    PDXObject pdxObject = resourceHandler.getXObject(xObjectName);
                    if (pdxObject == null) {
                        processContentOperator(result, rawOperator, arguments, operatorIndex, operatorIndexesToStreamInfosMap, operatorName, operatorStreamKey);
                        break;
                    }
                    pdxObject.setKey(ASAtom.STRUCT_PARENTS,
                        COSInteger.construct(xObjectStructParent));
                    StaticResources.getDocument().getDocument().addChangedObject(pdxObject.getObject());
                    PDXForm pdxForm = (PDXForm)pdxObject;
                    GraphicsState xFormGraphicsState = graphicsState.clone();
                    AutoTaggingProcessor.setUpContents(pdxForm.getObject(), new ChunksWriter(xFormGraphicsState,
                        resourceHandler.getExtendedResources(pdxForm.getResources())).processTokens(
                            ChunksWriter.getTokens(pdxForm), xObjectOperatorStreamKey));
                    // Preserve the Do operator in the parent stream so the XObject is still invoked
                    result.addAll(arguments);
                    result.add(rawOperator);
                } else {
                    processContentOperator(result, rawOperator, arguments, operatorIndex, operatorIndexesToStreamInfosMap, operatorName, operatorStreamKey);
                }
                break;
            case Operators.TJ_SHOW:
            case Operators.TJ_SHOW_POS:
            case Operators.QUOTE:
            case Operators.DOUBLE_QUOTE:
            case Operators.BI:
            case Operators.F_FILL:
            case Operators.F_FILL_OBSOLETE:
            case Operators.F_STAR_FILL:
            case Operators.B_CLOSEPATH_FILL_STROKE:
            case Operators.B_STAR_CLOSEPATH_EOFILL_STROKE:
            // B and B* (fill-then-stroke without closing) were absent, so those
            // paints were never wrapped. Left out, they would also carry the
            // buffered path back outside its span.
            case Operators.B_FILL_STROKE:
            case Operators.B_STAR_EOFILL_STROKE:
            case Operators.S_CLOSE_STROKE:
            case Operators.S_STROKE:
                processContentOperator(result, rawOperator, arguments, operatorIndex, operatorIndexesToStreamInfosMap, operatorName, operatorStreamKey);
                break;
            case org.verapdf.model.tools.constants.Operators.GS:
                PDExtGState extGState = this.resourceHandler.getExtGState(getLastCOSName(arguments));
                this.graphicsState.copyPropertiesFromExtGState(extGState);
                result.addAll(arguments);
                result.add(rawOperator);
                break;
            case org.verapdf.model.tools.constants.Operators.TF:
                this.graphicsState.getTextState().setTextFont(resourceHandler.getFont(getFirstCOSName(arguments)));
                result.addAll(arguments);
                result.add(rawOperator);
                break;
            case Operators.RE:
            case Operators.M_MOVE_TO:
            case Operators.L_LINE_TO:
            case Operators.C_CURVE_TO:
            case Operators.V:
            case Operators.Y:
            case Operators.H_CLOSEPATH:
                // Hold the path until its paint operator arrives, so both land
                // inside the same marked-content sequence.
                pendingPath.addAll(arguments);
                pendingPath.add(rawOperator);
                break;
            case Operators.N:
            case Operators.W_CLIP:
            case Operators.W_STAR_EOCLIP:
                // A clip consumes the path without painting it: nothing is drawn,
                // so there is no content to tag. Emit as-is.
                flushPendingPath(result);
                result.addAll(arguments);
                result.add(rawOperator);
                break;
            default:
//                    if (!Operators.D_SET_DASH.equals(operatorName)) {
                result.addAll(arguments);
                result.add(rawOperator);
//                    }
                break;
        }
    }

    private void processContentOperator(List<Object> result, Operator rawOperator, List<COSBase> arguments, int operatorIndex,
                                        Map<Integer, Set<StreamInfo>> operatorIndexesToStreamInfosMap, String operatorName,
                                        OperatorStreamKey operatorStreamKey) {
        Set<StreamInfo> streamInfos = operatorIndexesToStreamInfosMap.get(operatorIndex);
        if (streamInfos == null || streamInfos.isEmpty()) {
            writeMarkedContent(result, arguments, rawOperator, operatorName, null, null);
        } else {
            if (streamInfos.size() == 1) {
                Integer mcid = streamInfos.iterator().next().getMcid();
                writeMarkedContent(result, arguments, rawOperator, operatorName, mcid, operatorStreamKey);
            } else {
                List<StreamInfo> streamInfosList = updateStreamInfos(streamInfos);
                Map<StreamInfo, COSObject> newArguments = getArguments(arguments.get(arguments.size() - 1), streamInfosList);
                for (Map.Entry<StreamInfo, COSObject> entry : newArguments.entrySet()) {
                    arguments.set(arguments.size() - 1, entry.getValue().get());
                    writeMarkedContent(result, arguments, rawOperator, operatorName, entry.getKey().getMcid(), operatorStreamKey);
                }
            }
        }
    }

    private static String getStructureType(Integer mcid, OperatorStreamKey operatorStreamKey) {
        if (mcid == null || operatorStreamKey == null) return null;
        List<COSObject> parents = AutoTaggingProcessor.getStructParents().get(operatorStreamKey);
        if (parents == null) {
            CHUNKS_LOGGER.warning("structParents: no entry for key page=" + operatorStreamKey.getPageNumber() + " xobj=" + operatorStreamKey.getXObjectName() + " (available keys: " + AutoTaggingProcessor.getStructParents().keySet().stream().map(k -> "p"+k.getPageNumber()+"x"+k.getXObjectName()).collect(java.util.stream.Collectors.joining(",")) + ")");
            return null;
        }
        if (mcid >= parents.size()) {
            CHUNKS_LOGGER.warning("structParents: mcid=" + mcid + " out of range (size=" + parents.size() + ") for key page=" + operatorStreamKey.getPageNumber() + " xobj=" + operatorStreamKey.getXObjectName());
            return null;
        }
        COSObject structElem = parents.get(mcid);
        if (structElem == null) return null;
        COSObject typeObj = structElem.getKey(ASAtom.S);
        if (typeObj == null || typeObj.empty()) return null;
        return typeObj.getString();
    }

    /**
     * Emits any buffered path outside a marked-content sequence.
     *
     * <p>Used when the path is discarded or consumed as a clip rather than
     * painted: nothing reaches the page, so there is nothing to tag.
     */
    private void flushPendingPath(List<Object> result) {
        if (!pendingPath.isEmpty()) {
            result.addAll(pendingPath);
            pendingPath.clear();
        }
    }

    private void writeMarkedContent(List<Object> result, List<COSBase> arguments, Operator token,
                                           String operatorName, Integer mcid, OperatorStreamKey operatorStreamKey) {
        if (mcid == null) {
            result.add(COSName.construct(TaggedPDFConstants.ARTIFACT).getDirectBase());
            result.add(Operator.getOperator(Operators.BMC));
        } else {
            String tagName;
            if (Operators.BI.equals(operatorName) || Operators.DO.equals(operatorName)) {
                tagName = TaggedPDFConstants.FIGURE;
            } else {
                String structType = getStructureType(mcid, operatorStreamKey);
                tagName = (structType != null) ? structType : TaggedPDFConstants.SPAN;
            }
            result.add(COSName.construct(tagName).getDirectBase());
            COSObject dictionary = COSDictionary.construct();
            dictionary.setKey(ASAtom.MCID, COSInteger.construct(mcid));
            result.add(dictionary.getDirectBase());
            result.add(Operator.getOperator(Operators.BDC));
        }
        // Inside the span: the construction operators that define what this
        // paint operator draws, then the paint operator itself.
        result.addAll(pendingPath);
        pendingPath.clear();
        result.addAll(arguments);
        result.add(token);
        result.add(Operator.getOperator(Operators.EMC));
    }

    private Map<StreamInfo, COSObject> getArguments(COSBase object, List<StreamInfo> streamInfos) {
        Map<StreamInfo, COSObject> map = new TreeMap<>();
        if (object.getType() == COSObjType.COS_STRING) {
            Queue<StreamInfo> streamInfoQueue = new LinkedList<>(streamInfos);
            processString((COSString)object, map, streamInfoQueue, null, 0);
        } else if (object.getType() == COSObjType.COS_ARRAY) {
            int stringIndex = 0;
            List<COSObject> array = new ArrayList<>();
            Queue<StreamInfo> streamInfoQueue = new LinkedList<>(streamInfos);
            for (COSObject element : (COSArray)object.getDirectBase()) {
                if (element.getType() == COSObjType.COS_STRING) {
                    stringIndex = processString((COSString)element.get(), map, streamInfoQueue, array, stringIndex);
                } else if (element.getType().isNumber()) {
                    array.add(element);
                }
            }
            if (!array.isEmpty()) {
                StreamInfo peekInfo = streamInfoQueue.peek();
                COSObject target = peekInfo != null ? map.get(peekInfo) : null;
                if (target != null && target.getType() == COSObjType.COS_ARRAY) {
                    COSArray currentArray = (COSArray) target.get();
                    for (COSObject element : array) {
                        currentArray.add(element);
                    }
                } else {
                    CHUNKS_LOGGER.warning("Issue during text operator processing");
                }
            }
        }
        return map;
    }

    public int processString(COSString string, Map<StreamInfo, COSObject> map, Queue<StreamInfo> streamInfoQueue,
                             List<COSObject> array, int stringIndex) {
        int currentStringIndex = stringIndex;
        int currentBytesIndex = 0;
        int dif = 0;
        byte[] bytes = string.get();
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            int available = inputStream.available();
            while (inputStream.available() > 0) {
                int code = graphicsState.getTextState().getTextFont().readCode(inputStream);
                String value = graphicsState.getTextState().getTextFont().toUnicode(code, false);
                if (value == null) {
                    value = StaticContainers.getIsIgnoreCharactersWithoutUnicode() ? "" : ChunkParser.REPLACEMENT_CHARACTER_STRING;
                }
                int newAvailable = inputStream.available();
                dif += available - newAvailable;
                available = newAvailable;
                int length = streamInfoQueue.peek().getEndIndex() - currentStringIndex;
                currentStringIndex += value.length();
                if (length <= value.length()) {
                    COSObject cosString = COSString.construct(Arrays.copyOfRange(bytes, currentBytesIndex, currentBytesIndex + dif), string.isHexadecimal());
                    if (array != null) {
                        array.add(cosString);
                        map.put(streamInfoQueue.peek(), new COSObject(new COSArray(array)));
                    } else {
                        map.put(streamInfoQueue.peek(), cosString);
                    }
                    currentBytesIndex += dif;
                    dif = 0;
                    if (array != null) {
                        array.clear();
                    }
                    if (streamInfoQueue.size() > 1) {
                        streamInfoQueue.poll();
                    }
                }
            }
        } catch (IOException e) {
            CHUNKS_LOGGER.warning("Failed to process font string: " + e.getMessage());
        }
        if (array != null && dif > 0) {
            array.add(COSString.construct(Arrays.copyOfRange(bytes, currentBytesIndex, currentBytesIndex + dif), string.isHexadecimal()));
        }
        return currentStringIndex;
    }

    private static List<StreamInfo> updateStreamInfos(Set<StreamInfo> streamInfos) {
        Iterator<StreamInfo> streamInfoIterator = streamInfos.iterator();
        List<StreamInfo> newStreamInfos = new ArrayList<>();
        StreamInfo streamInfo = null;
        int currentIndex = 0;
        while (streamInfoIterator.hasNext()) {
            streamInfo = streamInfoIterator.next();
            if (currentIndex < streamInfo.getStartIndex()) {
                newStreamInfos.add(new StreamInfo(streamInfo.getOperatorIndex(), streamInfo.getXObjectName(),
                    streamInfo.getXImageObjectKey(), currentIndex, streamInfo.getStartIndex(), streamInfo.getLength(), null));
            }
            currentIndex = streamInfo.getEndIndex();
            newStreamInfos.add(streamInfo);
        }
        if (currentIndex < streamInfo.getLength()) {
            newStreamInfos.add(new StreamInfo(streamInfo.getOperatorIndex(), streamInfo.getXObjectName(),
                streamInfo.getXImageObjectKey(), streamInfo.getEndIndex(), streamInfo.getLength(), streamInfo.getLength(), null));
        }
        return newStreamInfos;
    }

    private static COSName getFirstCOSName(List<COSBase> arguments) {
        COSBase lastElement = arguments.isEmpty() ? null : arguments.get(0);
        if (lastElement instanceof COSName) {
            return (COSName) lastElement;
        }
        return null;
    }

    private static COSName getLastCOSName(List<COSBase> arguments) {
        COSBase lastElement = arguments.isEmpty() ? null : arguments.get(arguments.size() - 1);
        if (lastElement instanceof COSName) {
            return (COSName) lastElement;
        }
        return null;
    }
}
