/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSBase;
import org.verapdf.cos.COSKey;
import org.verapdf.cos.COSName;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.PDResourcesHandler;
import org.verapdf.pd.images.PDXForm;
import org.verapdf.pd.images.PDXObject;
import org.verapdf.wcag.algorithms.entities.content.IChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The operators of a page that paint in an {@code /Artifact} marked-content sequence (#801).
 *
 * <p>ISO 32000-1 14.8.2.2: an artifact is not part of the content. veraPDF gives the content of a
 * page that has no MCID as one list, so text in an {@code /Artifact} sequence and untagged text are
 * not separated there. This class reads the content streams with the tokens that veraPDF uses
 * ({@link ChunksWriter#getTokens}), so the operator index of a {@link StreamInfo} points to the same
 * operator. A stream info names a form XObject only by the name of its {@code Do} operator. If two
 * forms of a page have the same name, the operators of that name are not used.
 */
final class ArtifactOperators {

    private static final Logger LOGGER = Logger.getLogger(ArtifactOperators.class.getCanonicalName());

    /** If a page has forms below this depth, no operator of the page counts as an artifact. */
    private static final int MAX_FORM_DEPTH = 32;

    /** For each content stream (null for the page, else the form name), its operators in an artifact sequence. */
    private final Map<String, Set<Integer>> ownArtifactOperators = new HashMap<>();
    /** For each form name: true if each {@code Do} of the form is in an artifact sequence. */
    private final Map<String, Boolean> drawnOnlyInArtifacts = new HashMap<>();
    private final Map<String, COSKey> formKeys = new HashMap<>();
    private final Set<String> sharedNames = new HashSet<>();
    /** For each form name, the forms read with that name: not in an artifact sequence, and in one. */
    private final Map<String, Set<COSKey>> formsRead = new HashMap<>();
    private final Map<String, Set<COSKey>> formsReadInArtifacts = new HashMap<>();
    private boolean incomplete;

    private ArtifactOperators() {
    }

    /** Reads the content of the page. If the content cannot be read, no operator counts as an artifact. */
    static ArtifactOperators of(PDPage page) {
        ArtifactOperators result = new ArtifactOperators();
        try {
            PDResourcesHandler resources = PDResourcesHandler.getInstance(page.getResources(), page.isInheritedResources());
            result.read(ChunksWriter.getTokens(page.getContent()), resources, null, false, 0);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Cannot read the artifact sequences of a page: " + e.getMessage());
            return new ArtifactOperators();
        }
        return result.incomplete ? new ArtifactOperators() : result;
    }

    /**
     * True if each operator that painted the chunk is in an artifact sequence. A space that veraPDF adds for a gap
     * in a TJ array has the operator index -1, and is skipped. A chunk with no other stream info is not an artifact.
     */
    boolean isArtifact(IChunk chunk) {
        List<StreamInfo> streamInfos = chunk.getStreamInfos();
        boolean painted = false;
        if (streamInfos != null) {
            for (StreamInfo streamInfo : streamInfos) {
                if (streamInfo.getOperatorIndex() < 0) {
                    continue;
                }
                if (!contains(streamInfo.getXObjectName(), streamInfo.getOperatorIndex())) {
                    return false;
                }
                painted = true;
            }
        }
        return painted;
    }

    private boolean contains(String stream, int operatorIndex) {
        if (stream != null && Boolean.TRUE.equals(drawnOnlyInArtifacts.get(stream))) {
            return true;
        }
        if (stream != null && sharedNames.contains(stream)) {
            return false;
        }
        Set<Integer> operators = ownArtifactOperators.get(stream);
        return operators != null && operators.contains(operatorIndex);
    }

    private void read(List<Object> tokens, PDResourcesHandler resources, String stream, boolean drawnInArtifact, int depth) {
        Deque<Boolean> enclosing = new ArrayDeque<>();
        boolean inArtifact = false;
        List<COSBase> arguments = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            Object token = tokens.get(index);
            if (token instanceof COSBase) {
                arguments.add((COSBase) token);
                continue;
            }
            if (!(token instanceof Operator)) {
                continue;
            }
            switch (((Operator) token).getOperator()) {
                case Operators.BMC:
                case Operators.BDC:
                    enclosing.push(inArtifact);
                    inArtifact = inArtifact || isArtifactTag(arguments);
                    break;
                case Operators.EMC:
                    inArtifact = !enclosing.isEmpty() && enclosing.pop();
                    break;
                case Operators.DO:
                    readForm(arguments, resources, drawnInArtifact || inArtifact, depth);
                    break;
                default:
                    break;
            }
            if (inArtifact) {
                ownArtifactOperators.computeIfAbsent(stream, key -> new HashSet<>()).add(index);
            }
            arguments.clear();
        }
    }

    private void readForm(List<COSBase> arguments, PDResourcesHandler resources, boolean drawnInArtifact, int depth) {
        COSBase last = arguments.isEmpty() ? null : arguments.get(arguments.size() - 1);
        if (!(last instanceof COSName)) {
            return;
        }
        String name = last.getString();
        drawnOnlyInArtifacts.merge(name, drawnInArtifact, Boolean::logicalAnd);
        PDXObject drawn = resources.getXObject((COSName) last);
        if (!(drawn instanceof PDXForm)) {
            return;
        }
        COSKey formKey = drawn.getObject().getKey();
        if (formKey == null || depth >= MAX_FORM_DEPTH) {
            incomplete = true;
            return;
        }
        COSKey knownKey = formKeys.putIfAbsent(name, formKey);
        if (knownKey != null && !knownKey.equals(formKey)) {
            sharedNames.add(name);
        }
        // Each form is read one time for each name and each value of drawnInArtifact. This also stops a cycle.
        Map<String, Set<COSKey>> seen = drawnInArtifact ? formsReadInArtifacts : formsRead;
        if (!seen.computeIfAbsent(name, key -> new HashSet<>()).add(formKey)) {
            return;
        }
        PDXForm form = (PDXForm) drawn;
        read(ChunksWriter.getTokens(form), resources.getExtendedResources(form.getResources()), name, drawnInArtifact, depth + 1);
    }

    private static boolean isArtifactTag(List<COSBase> arguments) {
        return !arguments.isEmpty() && arguments.get(0) instanceof COSName
            && ASAtom.ARTIFACT.equals(((COSName) arguments.get(0)).getName());
    }
}
