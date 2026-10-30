package org.opendataloader.pdf.autotagging;

import org.verapdf.cos.COSBase;
import org.verapdf.cos.COSName;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.PDResourcesHandler;
import org.verapdf.pd.images.PDXForm;
import org.verapdf.pd.images.PDXObject;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where each paint operator on a page lands, keyed the way a StreamInfo names
 * an operator: by stream ({@link OperatorStreamKey}) and token index.
 *
 * <p>Extraction groups vector paths into line-art windows and gives a window
 * one box. Text drawn as outlines is thousands of filled paths in one window,
 * one per letter, so the window's box cannot say which words a path belongs
 * to; this can. The box is the hull of the path's points after the current
 * transformation, control points included, without the stroke's width.
 */
public final class PaintBounds {

    private static final Set<String> PAINTS = Set.of(
        Operators.S_STROKE, Operators.S_CLOSE_STROKE, Operators.F_FILL, Operators.F_FILL_OBSOLETE,
        Operators.F_STAR_FILL, Operators.B_FILL_STROKE, Operators.B_STAR_EOFILL_STROKE,
        Operators.B_CLOSEPATH_FILL_STROKE, Operators.B_STAR_CLOSEPATH_EOFILL_STROKE);

    private PaintBounds() {
    }

    /**
     * The boxes of the paint operators of a page of the current document and of
     * the forms it draws. A form drawn twice is bounded where it is drawn first.
     */
    public static Map<OperatorStreamKey, Map<Integer, BoundingBox>> of(int pageNumber) {
        Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds = new HashMap<>();
        PDPage page = StaticResources.getDocument().getPages().get(pageNumber);
        PDResourcesHandler resources = PDResourcesHandler.getInstance(page.getResources(), page.isInheritedResources());
        walk(ChunksWriter.getTokens(page.getContent()), new OperatorStreamKey(pageNumber, null),
            identity(), resources, bounds, new HashSet<>());
        return bounds;
    }

    private static void walk(List<Object> tokens, OperatorStreamKey key, double[] ctm, PDResourcesHandler resources,
                             Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds, Set<OperatorStreamKey> entered) {
        if (!entered.add(key)) {
            return;
        }
        Map<Integer, BoundingBox> boxes = bounds.computeIfAbsent(key, k -> new HashMap<>());
        Deque<double[]> saved = new ArrayDeque<>();
        double[] current = ctm.clone();
        List<double[]> path = new ArrayList<>();
        List<Double> operands = new ArrayList<>();
        COSName lastName = null;
        for (int index = 0; index < tokens.size(); index++) {
            Object token = tokens.get(index);
            if (!(token instanceof Operator)) {
                if (token instanceof COSName) {
                    lastName = (COSName) token;
                } else if (token instanceof COSBase) {
                    Double number = ((COSBase) token).getReal();
                    if (number != null) {
                        operands.add(number);
                    }
                }
                continue;
            }
            String operator = ((Operator) token).getOperator();
            switch (operator) {
                case Operators.Q_GSAVE:
                    saved.push(current.clone());
                    break;
                case Operators.Q_GRESTORE:
                    if (!saved.isEmpty()) {
                        current = saved.pop();
                    }
                    break;
                case Operators.CM_CONCAT:
                    if (operands.size() >= 6) {
                        current = multiply(last(operands, 6), current);
                    }
                    break;
                case Operators.M_MOVE_TO:
                case Operators.L_LINE_TO:
                    addPoints(path, last(operands, 2), current);
                    break;
                case Operators.C_CURVE_TO:
                    addPoints(path, last(operands, 6), current);
                    break;
                case Operators.V:
                case Operators.Y:
                    addPoints(path, last(operands, 4), current);
                    break;
                case Operators.RE:
                    if (operands.size() >= 4) {
                        double[] r = last(operands, 4);
                        addPoints(path, new double[]{r[0], r[1], r[0] + r[2], r[1], r[0] + r[2], r[1] + r[3],
                            r[0], r[1] + r[3]}, current);
                    }
                    break;
                case Operators.N:
                    path.clear();
                    break;
                case Operators.DO:
                    drawForm(lastName, key, current, resources, bounds, entered);
                    break;
                default:
                    if (PAINTS.contains(operator)) {
                        if (!path.isEmpty()) {
                            boxes.put(index, boxOf(path, key.getPageNumber()));
                        }
                        path.clear();
                    }
                    break;
            }
            operands.clear();
            lastName = null;
        }
    }

    private static void drawForm(COSName name, OperatorStreamKey key, double[] ctm, PDResourcesHandler resources,
                                 Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds, Set<OperatorStreamKey> entered) {
        if (name == null || resources == null) {
            return;
        }
        PDXObject drawn = resources.getXObject(name);
        if (!(drawn instanceof PDXForm)) {
            return;
        }
        PDXForm form = (PDXForm) drawn;
        double[] matrix = form.getMatrix();
        double[] formCtm = matrix != null && matrix.length == 6 ? multiply(matrix, ctm) : ctm;
        walk(ChunksWriter.getTokens(form), new OperatorStreamKey(key.getPageNumber(), name.getString()), formCtm,
            resources.getExtendedResources(form.getResources()), bounds, entered);
    }

    private static double[] identity() {
        return new double[]{1, 0, 0, 1, 0, 0};
    }

    private static double[] last(List<Double> operands, int count) {
        double[] values = new double[count];
        int offset = operands.size() - count;
        for (int i = 0; i < count; i++) {
            values[i] = offset + i >= 0 ? operands.get(offset + i) : 0;
        }
        return values;
    }

    /** {@code a} then {@code b}, as PDF matrices compose: a × b. */
    private static double[] multiply(double[] a, double[] b) {
        return new double[]{
            a[0] * b[0] + a[1] * b[2],
            a[0] * b[1] + a[1] * b[3],
            a[2] * b[0] + a[3] * b[2],
            a[2] * b[1] + a[3] * b[3],
            a[4] * b[0] + a[5] * b[2] + b[4],
            a[4] * b[1] + a[5] * b[3] + b[5]};
    }

    private static void addPoints(List<double[]> path, double[] coordinates, double[] ctm) {
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            double x = coordinates[i];
            double y = coordinates[i + 1];
            path.add(new double[]{x * ctm[0] + y * ctm[2] + ctm[4], x * ctm[1] + y * ctm[3] + ctm[5]});
        }
    }

    private static BoundingBox boxOf(List<double[]> points, int pageNumber) {
        double left = Double.MAX_VALUE;
        double bottom = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double top = -Double.MAX_VALUE;
        for (double[] point : points) {
            left = Math.min(left, point[0]);
            right = Math.max(right, point[0]);
            bottom = Math.min(bottom, point[1]);
            top = Math.max(top, point[1]);
        }
        return new BoundingBox(pageNumber, left, bottom, right, top);
    }
}
