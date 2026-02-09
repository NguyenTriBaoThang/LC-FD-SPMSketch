package utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class CsrPosMap {
    // For one pattern:
    // sid[i] is the SID
    // off[i]..off[i+1] slice in posData
    public final int[] sid;
    public final int[] off;
    public final byte[] posData; // concatenated GapVarintPosList bytes
    public final int entryCount; // == sid.length
    public final int totalPid;   // total PIDs (for memory stats)

    private CsrPosMap(int[] sid, int[] off, byte[] posData, int totalPid) {
        this.sid = sid;
        this.off = off;
        this.posData = posData;
        this.entryCount = sid.length;
        this.totalPid = totalPid;
    }

    public Slice getSliceByIndex(int idx, int pidCountForThisSid) {
        int start = off[idx];
        int end = off[idx + 1];
        return new Slice(Arrays.copyOfRange(posData, start, end), pidCountForThisSid);
    }

    public static final class Slice {
        public final byte[] bytes;
        public final int pidCount;
        public Slice(byte[] bytes, int pidCount) { this.bytes = bytes; this.pidCount = pidCount; }
    }

    /** Build CSR from Map<SID, GapVarintPosList>. SID keys can be unsorted. */
    public static CsrPosMap build(Map<Integer, GapVarintPosList> map) {
        int n = map.size();
        int[] sid = new int[n];
        int k = 0;
        for (int s : map.keySet()) sid[k++] = s;
        Arrays.sort(sid);

        // compute offsets + concat
        int[] off = new int[n + 1];
        int totalBytes = 0;
        int totalPid = 0;

        for (int i = 0; i < n; i++) {
            GapVarintPosList lst = map.get(sid[i]);
            byte[] b = lst.bytes();
            off[i] = totalBytes;
            totalBytes += b.length;
            totalPid += lst.size();
        }
        off[n] = totalBytes;

        byte[] posData = new byte[totalBytes];
        int cursor = 0;
        for (int i = 0; i < n; i++) {
            GapVarintPosList lst = map.get(sid[i]);
            byte[] b = lst.bytes();
            System.arraycopy(b, 0, posData, cursor, b.length);
            cursor += b.length;
        }

        return new CsrPosMap(sid, off, posData, totalPid);
    }

    // Helper for fast building in code:
    public static Map<Integer, GapVarintPosList> newSidToPosListMap() {
        return new HashMap<>();
    }
}
