package org.opendataloader.pdf.autotagging;

import org.opendataloader.pdf.contentstream.ContentStreamTokens;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSKey;
import org.verapdf.cos.COSName;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.PDResourcesHandler;
import org.verapdf.pd.images.PDXForm;
import org.verapdf.pd.images.PDXObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How many times each Form XObject is drawn in a document.
 *
 * <p>A form has one {@code /StructParents}, so the marked content in it can belong to one
 * structure context only. PDF/UA-1 7.20 test 2 (veraPDF {@code isUniqueSemanticParent}) fails
 * a form that has {@code /StructParents} and is drawn more than once, in the document: it
 * counts every reference, so a form drawn on two pages counts as much as one drawn twice on a
 * page, and a form drawn from a form that is itself drawn twice is drawn twice.
 *
 * <p>The draws form a graph: a page draws forms, a form draws forms. Each form is scanned once
 * and the counts are carried along the graph in one pass, so the work grows with the number of
 * forms and not with the number of paths to them (a layer of w forms that each draw the whole
 * next layer has w^depth paths). A form that draws itself, directly or not, is not followed
 * round again.
 */
public final class FormUses {

    /**
     * Deepest form nesting scanned. Below it the draws are not counted, and every form is then
     * reported as drawn often: a form that cannot be shown to be drawn once is not given structure.
     */
    static final int MAX_DEPTH = 64;
    /** A count above one is all that matters; this keeps nested multiplication from overflowing. */
    private static final int CAP = 1000;

    /**
     * A form as drawn from some resources. A form with no {@code /Resources} finds the names it
     * draws in the resources of whoever draws it, so the same form drawn from two pages can draw
     * two different forms; a form with resources of its own does not depend on its caller.
     */
    private static final class Node {
        final PDXForm form;
        final PDResourcesHandler resources;
        final COSKey key;
        final Map<Node, Integer> draws = new LinkedHashMap<>();
        int state;
        long weight;

        Node(PDXForm form, PDResourcesHandler resources, COSKey key) {
            this.form = form;
            this.resources = resources;
            this.key = key;
        }
    }

    private static final class NodeKey {
        final COSKey form;
        final Object context;

        NodeKey(COSKey form, Object context) {
            this.form = form;
            this.context = context;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NodeKey && form.equals(((NodeKey) other).form)
                && context == ((NodeKey) other).context;
        }

        @Override
        public int hashCode() {
            return Objects.hash(form, System.identityHashCode(context));
        }
    }

    private final Map<NodeKey, Node> nodes = new HashMap<>();
    private final Map<COSKey, Long> uses = new HashMap<>();
    private final List<Node> finished = new ArrayList<>();
    private boolean incomplete;

    private FormUses() {
    }

    /** Counts the draws of the forms of these pages. */
    public static FormUses of(List<PDPage> pages) {
        FormUses counter = new FormUses();
        Map<Node, Integer> fromPages = new LinkedHashMap<>();
        for (PDPage page : pages) {
            PDResourcesHandler resources = PDResourcesHandler.getInstance(page.getResources(), page.isInheritedResources());
            counter.drawnIn(ContentStreamTokens.getTokens(page.getContent()), resources, fromPages);
        }
        for (Node node : fromPages.keySet()) {
            counter.scan(node, 1);
        }
        for (Map.Entry<Node, Integer> entry : fromPages.entrySet()) {
            entry.getKey().weight = Math.min(CAP, entry.getKey().weight + entry.getValue());
        }
        // Reverse finishing order is a topological order: a form comes after every form that
        // draws it, so its weight is complete when it is passed on.
        for (int i = counter.finished.size() - 1; i >= 0; i--) {
            Node node = counter.finished.get(i);
            counter.uses.merge(node.key, node.weight, (a, b) -> Math.min(CAP, a + b));
            for (Map.Entry<Node, Integer> draw : node.draws.entrySet()) {
                draw.getKey().weight = Math.min(CAP, draw.getKey().weight + node.weight * draw.getValue());
            }
        }
        return counter;
    }

    /**
     * How many times the form is drawn; a count above one means it is shared. A form that was
     * not met is 0. When the scan could not reach the bottom of the nesting, every form counts
     * as drawn often.
     */
    public long usesOf(COSKey form) {
        if (incomplete) {
            return CAP;
        }
        return uses.getOrDefault(form, 0L);
    }

    /** Scans the forms this form draws, depth first. */
    private void scan(Node node, int depth) {
        if (node.state != 0) {
            return;
        }
        node.state = 1;
        if (depth > MAX_DEPTH) {
            incomplete = true;
            node.state = 2;
            finished.add(node);
            return;
        }
        drawnIn(ContentStreamTokens.getTokens(node.form), node.resources.getExtendedResources(node.form.getResources()),
            node.draws);
        // A draw of a form that is being scanned is a cycle; following it would not end.
        node.draws.keySet().removeIf(drawn -> drawn.state == 1);
        for (Node drawn : new ArrayList<>(node.draws.keySet())) {
            scan(drawn, depth + 1);
        }
        node.state = 2;
        finished.add(node);
    }

    /** The forms that these tokens draw, with how often, added to {@code into}. */
    private void drawnIn(List<Object> tokens, PDResourcesHandler resources, Map<Node, Integer> into) {
        COSName last = null;
        for (Object token : tokens) {
            if (token instanceof COSName) {
                last = (COSName) token;
            } else if (token instanceof Operator) {
                if (Operators.DO.equals(((Operator) token).getOperator()) && last != null && resources != null) {
                    PDXObject xObject = resources.getXObject(last);
                    COSKey key = xObject instanceof PDXForm ? xObject.getObject().getKey() : null;
                    if (key != null) {
                        PDXForm form = (PDXForm) xObject;
                        Object context = form.getObject().knownKey(ASAtom.RESOURCES) ? null : resources;
                        Node node = nodes.computeIfAbsent(new NodeKey(key, context), k -> new Node(form, resources, key));
                        into.merge(node, 1, (a, b) -> Math.min(CAP, a + b));
                    }
                }
                last = null;
            }
        }
    }
}
