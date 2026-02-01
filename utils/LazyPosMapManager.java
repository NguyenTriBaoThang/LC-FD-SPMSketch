package utils;

import java.util.HashMap;
import java.util.Map;

public final class LazyPosMapManager {

    private final LruCache<String, CsrPosMap> cache;

    public LazyPosMapManager(int maxActivePatterns) {
        this.cache = new LruCache<>(maxActivePatterns);
    }

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

    public void deactivate(PatternEntry p) {
        p.posMap = null;
    }

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

                GapVarintPosList validY = null;

                if (itX.hasNext()) {
                    int xPos = itX.next(); 

                    while (itY.hasNext()) {
                        int yPos = itY.next();

                        while (itX.hasNext() && itX.peek() < yPos) {
                            xPos = itX.next();
                        }

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

    public interface PosMaterializer {
        CsrPosMap materialize(String patternKey);
    }

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
