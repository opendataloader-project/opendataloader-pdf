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
package org.opendataloader.pdf.graph;

import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a canonical document graph from extracted objects.
 */
public final class GraphBuilder {

    private GraphBuilder() {
    }

    public static List<GraphNode> build(List<List<IObject>> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (List<IObject> pageContents : contents) {
            if (pageContents == null) {
                continue;
            }
            for (IObject object : pageContents) {
                if (object == null) {
                    continue;
                }
                nodes.add(buildNode(object));
            }
        }
        return List.copyOf(nodes);
    }

    private static GraphNode buildNode(IObject object) {
        if (object instanceof SemanticHeading) {
            return buildHeadingNode((SemanticHeading) object);
        }
        if (object instanceof SemanticFormula) {
            return buildEquationNode((SemanticFormula) object);
        }
        if (object instanceof SemanticCaption) {
            return buildCaptionNode((SemanticCaption) object);
        }
        return new GraphNode(
            object.getPageNumber(),
            copy(object.getBoundingBox()),
            object.getRecognizedStructureId(),
            null,
            "Unsupported type: " + object.getClass().getSimpleName()
        );
    }

    private static HeadingNode buildHeadingNode(SemanticHeading heading) {
        String text = heading.getValue();
        return new HeadingNode(
            heading.getHeadingLevel(),
            text,
            text,
            heading.getPageNumber(),
            copy(heading.getBoundingBox()),
            heading.getRecognizedStructureId(),
            null
        );
    }

    private static EquationNode buildEquationNode(SemanticFormula formula) {
        String latex = formula.getLatex();
        return new EquationNode(
            latex,
            true,
            null,
            latex,
            formula.getPageNumber(),
            copy(formula.getBoundingBox()),
            formula.getRecognizedStructureId(),
            null
        );
    }

    private static CaptionNode buildCaptionNode(SemanticCaption caption) {
        String text = caption.getValue();
        return new CaptionNode(
            "caption",
            null,
            caption.getLinkedContentId(),
            text,
            text,
            caption.getPageNumber(),
            copy(caption.getBoundingBox()),
            caption.getRecognizedStructureId(),
            null
        );
    }

    private static BoundingBox copy(BoundingBox bbox) {
        return bbox == null ? null : new BoundingBox(bbox);
    }
}
