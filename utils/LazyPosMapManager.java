package utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazy PosMap manager:
 * - PatternEntry.posMap is null unless pattern is active (needed for ExtendP)
 * - Active posMaps are stored as CSR (CsrPosMap) + gap/varint encoded PosLists
 */
public final class LazyPosMapManager {

    private final LruCache<String, CsrPosMap> cache;

    public LazyPosMapManager(int maxActivePatterns) {
        this.cache = new LruCache<>(maxActivePatterns);
    }

    /** Ensure pattern has active posMap (CSR). If absent, materialize on demand (lazy). */
    public void ensureActive(PatternEntry p, PosMaterializer materializer) {
        if (p.posMap != null) return;

        CsrPosMap cached = cache.get(p.key);
        if (cached != null) {
            p.posMap = cached;
            return;
        }

        CsrPosMap built = materializer.materialize(p.key);
        p.posMap = built;
        cache.put(p.key, built);
    }

    /** Drop active map from pattern (keep in cache). */
    public void deactivate(PatternEntry p) {
        p.posMap = null;
    }

    /**
     * ExtendP: build positional map for newPattern = concat(prefixX, patternY)
     *
     * Inputs:
     *  - xMap: CSR posMap of prefix (positions of last item of prefix)
     *  - xPidCount: map SID->pidCount for xMap's PosList
     *  - yMap: CSR posMap of Y (positions of last item of Y)
     *  - yPidCount: map SID->pidCount for yMap's PosList
     *
     * Output:
     *  - CSR posMap for new pattern (SID->valid Y positions)
     *
     * NOTE:
     *  - This function does not update sketches (SIDMros). You do it outside.
     */
    public CsrPosMap extendP(
            CsrPosMap xMap, Map<Integer, Integer> xPidCount,
            CsrPosMap yMap, Map<Integer, Integer> yPidCount) {

        Map<Integer, GapVarintPosList> out = CsrPosMap.newSidToPosListMap();

        int i = 0, j = 0;
        while (i < xMap.sid.length && j < yMap.sid.length) {
            int sx = xMap.sid[i];
            int sy = yMap.sid[j];

            if (sx < sy) { i++; continue; }
            if (sy < sx) { j++; continue; }

            int sid = sx;

            int xCount = xPidCount.getOrDefault(sid, 0);
            int yCount = yPidCount.getOrDefault(sid, 0);

            if (xCount > 0 && yCount > 0) {
                byte[] xBytes = sliceBytes(xMap, i);
                byte[] yBytes = sliceBytes(yMap, j);

                GapVarintPosList.Iterator rawX = GapVarintPosList.iteratorFromBytes(xBytes, xCount);
                GapVarintPosList.Iterator rawY = GapVarintPosList.iteratorFromBytes(yBytes, yCount);

                BufferedPidIter itX = new BufferedPidIter(rawX);
                BufferedPidIter itY = new BufferedPidIter(rawY);

                // Extend logic (standard):
                // keep yPos if exists xPos < yPos.
                GapVarintPosList validY = null;

                // If X is empty, none can be valid
                if (itX.hasNext()) {
                    int xPos = itX.next(); // current x

                    while (itY.hasNext()) {
                        int yPos = itY.next();

                        // Move xPos forward while next x is still < yPos
                        while (itX.hasNext() && itX.peek() < yPos) {
                            xPos = itX.next();
                        }

                        // Now xPos is the largest x we've consumed (< current yPos) or >= yPos
                        if (xPos < yPos) {
                            if (validY == null) validY = new GapVarintPosList();
                            validY.add(yPos);
                        }
                    }
                }

                if (validY != null && validY.size() > 0) {
                    out.put(sid, validY);
                }
            }

            i++; j++;
        }

        return CsrPosMap.build(out);
    }

    // -------------------- helper types --------------------

    /**
     * Materializer builds CSR posMap for a given pattern key when we need it.
     * (Lazy PosMap)
     */
    public interface PosMaterializer {
        CsrPosMap materialize(String patternKey);
    }

    /** One-item peek buffer wrapper around GapVarintPosList.Iterator (no inheritance). */
    private static final class BufferedPidIter {
        private final GapVarintPosList.Iterator it;
        private boolean hasBuf;
        private int buf;

        BufferedPidIter(GapVarintPosList.Iterator it) {
            this.it = it;
            this.hasBuf = false;
        }

        boolean hasNext() {
            return hasBuf || it.hasNext();
        }

        int peek() {
            if (!hasBuf) {
                if (!it.hasNext()) throw new IllegalStateException("peek() on empty iterator");
                buf = it.next();
                hasBuf = true;
            }
            return buf;
        }

        int next() {
            if (hasBuf) {
                hasBuf = false;
                return buf;
            }
            return it.next();
        }
    }

    private static byte[] sliceBytes(CsrPosMap map, int idx) {
        int start = map.off[idx];
        int end = map.off[idx + 1];
        byte[] b = new byte[end - start];
        System.arraycopy(map.posData, start, b, 0, b.length);
        return b;
    }
}
