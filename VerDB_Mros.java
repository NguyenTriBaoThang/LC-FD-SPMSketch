import java.util.ArrayList;

/**
 * VerDB_Mros cải tiến theo bài báo LC-FD-SPMSketch.
 *
 * Cải tiến 2.3.3 — Nén PosList bằng gap-encoding + varint:
 *   Thay int[] pids (4 byte/PID tuyệt đối) bằng byte[] posData nén
 *   (first_pid + các gap varint-encoded, ~1-2 byte/gap).
 *
 * Cải tiến 2.3.4 — CSR-like PosMap phẳng:
 *   Thay int[][] (N object int[], mỗi cái 12B header) bằng:
 *     int[]  sids     — SID tăng dần
 *     int[]  off      — off[i] = byte offset bắt đầu PosList i trong posData
 *     int[]  firstPid — PID đầu tiên của mỗi PosList (để ExtendP đọc nhanh)
 *     byte[] posData  — toàn bộ gap-encoded pids nối liền, 1 object duy nhất
 *   → Loại bỏ N*12B header overhead, cải thiện memory locality.
 *
 * Cải tiến 2.3.1/2.3.2 — Support-only / Lazy PosMap:
 *   k-pattern (k>=2): k_MDBmap chỉ lưu Mros (support-only).
 *   VerDB_Mros chỉ tồn tại trong stack frame của GrowP đang dùng nó
 *   → tự động GC khi GrowP return (lazy lifecycle, không cần thêm code).
 */
public class VerDB_Mros {
    Mros UCID_Mros = new Mros(10, 16, 0.38, 8);

    // ── CSR storage (sau freeze) ──
    int[]  sids;      // SID tăng dần
    int[]  off;       // off[i] = byte offset trong posData của PosList i; off[cnt] = posData.length
    int[]  firstPid;  // firstPid[i] = PID đầu tiên của sids[i] (đọc trực tiếp, không decode)
    byte[] posData;   // gap-encoded pids của tất cả SID nối liền
    int    cnt;

    // ── Build buffers (tạm, được null sau freeze) ──
    private ArrayList<Integer> _bSids;
    private ArrayList<Integer> _bFirstPid;
    private ArrayList<byte[]>  _bEncoded;  // mảng byte đã encode cho từng SID
    int _curSid = -1;
    private int _curLastPid;
    private byte[] _curBuf = new byte[32];
    private int    _curOff;
    private int    _curFirstPid;
    private int    _curCount;

    public VerDB_Mros() {
        _bSids     = new ArrayList<>();
        _bFirstPid = new ArrayList<>();
        _bEncoded  = new ArrayList<>();
    }

    /** Constructor nhanh cho kết quả ExtendP (không cần build buffers). */
    VerDB_Mros(boolean forResult) { }

    /** Thêm (sid, pid) khi đọc file. Với mỗi item, sid tăng dần, pid tăng dần trong cùng sid. */
    public void buildAdd(int sid, int pid) {
        if (sid != _curSid) {
            flushCur();
            _curSid      = sid;
            _curFirstPid = pid;
            _curLastPid  = pid;
            _curOff      = 0;
            _curCount    = 1;
        } else {
            // Encode gap = pid - lastPid vào _curBuf
            int gap = pid - _curLastPid;
            _curLastPid = pid;
            if (_curOff + 5 > _curBuf.length) {
                byte[] grown = new byte[_curBuf.length * 2];
                System.arraycopy(_curBuf, 0, grown, 0, _curOff);
                _curBuf = grown;
            }
            _curOff = writeVarint(_curBuf, _curOff, gap);
            _curCount++;
        }
    }

    private void flushCur() {
        if (_curSid < 0) return;
        _bSids.add(_curSid);
        _bFirstPid.add(_curFirstPid);
        byte[] enc = new byte[_curOff];
        System.arraycopy(_curBuf, 0, enc, 0, _curOff);
        _bEncoded.add(enc);
        _curOff = 0;
    }

    /** Finalize: gộp tất cả thành CSR phẳng. Gọi 1 lần sau khi đọc file xong. */
    public void freeze() {
        flushCur();
        cnt      = _bSids.size();
        sids     = new int[cnt];
        off      = new int[cnt + 1];
        firstPid = new int[cnt];
        int total = 0;
        for (int i = 0; i < cnt; i++) total += _bEncoded.get(i).length;
        posData = new byte[total];

        int pos = 0;
        for (int i = 0; i < cnt; i++) {
            sids[i]     = _bSids.get(i);
            firstPid[i] = _bFirstPid.get(i);
            off[i]      = pos;
            byte[] enc  = _bEncoded.get(i);
            System.arraycopy(enc, 0, posData, pos, enc.length);
            pos += enc.length;
        }
        off[cnt] = pos;
        // Giải phóng buffers
        _bSids = null; _bFirstPid = null; _bEncoded = null; _curBuf = null;
    }

    public int getSupport() { return cnt; }

    /**
     * Giải mã PosList tại index i thành int[].
     * Dùng trong ExtendP khi cần duyệt toàn bộ PIDs.
     */
    public int[] decodePids(int i) {
        int start = off[i], end = off[i + 1];
        // Đếm số gap values để tính kích thước mảng
        // Cách nhanh: duyệt posData từ start→end đếm varint values
        int gapCount = 0, p = start;
        while (p < end) {
            while ((posData[p++] & 0x80) != 0) ;
            gapCount++;
        }
        int[] pids = new int[gapCount + 1];
        pids[0] = firstPid[i];
        p = start;
        for (int k = 1; k <= gapCount; k++) {
            int gap = 0, shift = 0;
            byte b;
            do { b = posData[p++]; gap |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0);
            pids[k] = pids[k - 1] + gap;
        }
        return pids;
    }

    // ── Varint helpers ──
    static int writeVarint(byte[] buf, int off, int v) {
        while ((v & ~0x7F) != 0) { buf[off++] = (byte)((v & 0x7F) | 0x80); v >>>= 7; }
        buf[off++] = (byte) v;
        return off;
    }
}