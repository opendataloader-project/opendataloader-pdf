package org.opendataloader.pdf.autotagging;

import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A page's paint operators, handed out by where they land, each once.
 *
 * <p>For text a page draws as outlines: whoever read the words, OCR, gives
 * their box, and the paths inside it are what draws them. A path that does not
 * fit in the box, a panel or rule behind the words, is not a letter of them.
 * An operator handed out once is not handed out again, so two boxes that touch
 * do not both claim a letter.
 */
public final class PagePaints {

    private static final Logger LOGGER = Logger.getLogger(PagePaints.class.getCanonicalName());

    private final int pageNumber;
    private final Supplier<Map<OperatorStreamKey, Map<Integer, BoundingBox>>> boundsOfPage;
    private Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds;
    private final Set<String> taken = new HashSet<>();

    public PagePaints(int pageNumber) {
        this(pageNumber, () -> PaintBounds.of(pageNumber));
    }

    PagePaints(int pageNumber, Supplier<Map<OperatorStreamKey, Map<Integer, BoundingBox>>> boundsOfPage) {
        this.pageNumber = pageNumber;
        this.boundsOfPage = boundsOfPage;
    }

    /**
     * The paint operators lying inside {@code box}, widened by {@code tolerance}
     * on each side, that nothing has taken; they are taken now. In drawing order.
     */
    public List<StreamInfo> takeInside(BoundingBox box, double tolerance) {
        if (bounds == null) {
            try {
                bounds = boundsOfPage.get();
            } catch (RuntimeException e) {
                // A content stream that cannot be walked: this page hands out
                // nothing, and its text stays as OCR read it. The rest of the
                // run goes on.
                LOGGER.log(Level.WARNING, "Page " + (pageNumber + 1) + ": paint operators not bounded", e);
                bounds = Map.of();
            }
        }
        List<StreamInfo> inside = new ArrayList<>();
        List<OperatorStreamKey> keys = new ArrayList<>(bounds.keySet());
        keys.sort(Comparator.comparing(k -> k.getXObjectName() == null ? "" : k.getXObjectName()));
        for (OperatorStreamKey key : keys) {
            List<Integer> indexes = new ArrayList<>(bounds.get(key).keySet());
            indexes.sort(null);
            for (int index : indexes) {
                BoundingBox paint = bounds.get(key).get(index);
                if (within(paint, box, tolerance) && taken.add(keyOf(key.getXObjectName(), index))) {
                    inside.add(new StreamInfo(index, key.getXObjectName(), 0, 0));
                }
            }
        }
        return inside;
    }

    /** Marks operators something else already holds, so they are not handed out. */
    public void markTaken(Collection<StreamInfo> infos) {
        for (StreamInfo info : infos) {
            taken.add(keyOf(info.getXObjectName(), info.getOperatorIndex()));
        }
    }

    private static boolean within(BoundingBox inner, BoundingBox outer, double tolerance) {
        return inner.getLeftX() >= outer.getLeftX() - tolerance
            && inner.getRightX() <= outer.getRightX() + tolerance
            && inner.getBottomY() >= outer.getBottomY() - tolerance
            && inner.getTopY() <= outer.getTopY() + tolerance;
    }

    private static String keyOf(String xObjectName, int index) {
        return Objects.toString(xObjectName, "") + "#" + index;
    }
}
