package org.opendataloader.pdf.autotagging;

import org.opendataloader.pdf.contentstream.ContentStreamTokens;
import org.verapdf.cos.COSKey;
import org.verapdf.cos.COSName;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.PDResourcesHandler;
import org.verapdf.pd.images.PDXForm;
import org.verapdf.pd.images.PDXObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How many times each Form XObject is drawn in a document.
 *
 * <p>A form has one {@code /StructParents}, so the marked content in it can belong to one
 * structure context only. PDF/UA-1 7.20 test 2 (veraPDF {@code isUniqueSemanticParent}) fails
 * a form that has {@code /StructParents} and is drawn more than once, in the document: it
 * counts every reference, so a form drawn on two pages counts as much as one drawn twice on a
 * page, and a form drawn from a form that is itself drawn twice is drawn twice.
 */
public final class FormUses {

    /** Deepest form nesting followed; a form can draw itself. */
    private static final int MAX_DEPTH = 8;
    /** A count above one is all that matters; this keeps nested multiplication from overflowing. */
    private static final int CAP = 1000;

    private final Map<COSKey, Map<COSKey, Integer>> drawnBy = new HashMap<>();
    private final Map<COSKey, Integer> uses = new HashMap<>();

    private static final class Seen {
        final PDXForm form;
        final PDResourcesHandler outer;

        Seen(PDXForm form, PDResourcesHandler outer) {
            this.form = form;
            this.outer = outer;
        }
    }

    private final Map<COSKey, Seen> formsSeen = new HashMap<>();

    private FormUses() {
    }

    /**
     * The number of draws of each form of the pages, by the form's object key. A form with no
     * object key cannot be drawn from two places and is not listed.
     */
    public static Map<COSKey, Integer> of(List<PDPage> pages) {
        FormUses counter = new FormUses();
        for (PDPage page : pages) {
            PDResourcesHandler resources = PDResourcesHandler.getInstance(page.getResources(), page.isInheritedResources());
            counter.visit(counter.drawnIn(ContentStreamTokens.getTokens(page.getContent()), resources), 1, new HashSet<>(), 0);
        }
        return counter.uses;
    }

    /** The forms that these tokens draw, with how often. */
    private Map<COSKey, Integer> drawnIn(List<Object> tokens, PDResourcesHandler resources) {
        Map<COSKey, Integer> drawn = new HashMap<>();
        COSName last = null;
        for (Object token : tokens) {
            if (token instanceof COSName) {
                last = (COSName) token;
            } else if (token instanceof Operator) {
                if (Operators.DO.equals(((Operator) token).getOperator()) && last != null && resources != null) {
                    PDXObject xObject = resources.getXObject(last);
                    COSKey key = xObject instanceof PDXForm ? xObject.getObject().getKey() : null;
                    if (key != null) {
                        drawn.merge(key, 1, (a, b) -> Math.min(CAP, a + b));
                        formsSeen.computeIfAbsent(key, k -> new Seen((PDXForm) xObject, resources));
                    }
                }
                last = null;
            }
        }
        return drawn;
    }

    private void visit(Map<COSKey, Integer> drawn, int weight, Set<COSKey> path, int depth) {
        for (Map.Entry<COSKey, Integer> entry : drawn.entrySet()) {
            COSKey key = entry.getKey();
            int times = Math.min(CAP, weight * entry.getValue());
            uses.merge(key, times, (a, b) -> Math.min(CAP, a + b));
            if (depth >= MAX_DEPTH || !path.add(key)) {
                continue;
            }
            Map<COSKey, Integer> inner = drawnBy.get(key);
            if (inner == null) {
                Seen seen = formsSeen.get(key);
                inner = drawnIn(ContentStreamTokens.getTokens(seen.form), seen.outer.getExtendedResources(seen.form.getResources()));
                drawnBy.put(key, inner);
            }
            visit(inner, times, path, depth + 1);
            path.remove(key);
        }
    }
}
