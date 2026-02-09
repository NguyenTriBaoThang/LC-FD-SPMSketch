package utils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import utils.Varint.IntRef;

public final class GapVarintPosList {
    private byte[] encoded = new byte[0];
    private int size = 0;
    private int first = -1;
    private int last = -1;

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int first() {
        if (size == 0) throw new IllegalStateException("empty");
        return first;
    }
    public int last() {
        if (size == 0) throw new IllegalStateException("empty");
        return last;
    }

    /** Add PID in non-decreasing order */
    public void add(int pid) {
        if (size > 0 && pid < last) {
            throw new IllegalArgumentException("PIDs must be non-decreasing. last=" + last + ", pid=" + pid);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length + 8);
        if (encoded.length > 0) out.writeBytes(encoded);

        if (size == 0) {
            Varint.writeUnsigned(out, pid);
            first = pid;
            last = pid;
        } else {
            Varint.writeUnsigned(out, pid - last);
            last = pid;
        }
        encoded = out.toByteArray();
        size++;
    }

    public byte[] bytes() { return encoded; }

    public Iterator iterator() {
        return new Iterator(encoded, size);
    }

    public static Iterator iteratorFromBytes(byte[] bytes, int pidCount) {
        return new Iterator(bytes, pidCount);
    }

    /** Debug: decode to List<Integer> */
    public List<Integer> toList() {
        List<Integer> res = new ArrayList<>(size);
        Iterator it = iterator();
        while (it.hasNext()) res.add(it.next());
        return res;
    }

    /** Create a new PosList containing only pids > threshold */
    public GapVarintPosList copyFilteredGreaterThan(int threshold) {
        GapVarintPosList out = new GapVarintPosList();
        Iterator it = iterator();
        while (it.hasNext()) {
            int v = it.next();
            if (v > threshold) out.add(v);
        }
        return out;
    }

    public static final class Iterator {
        private final byte[] data;
        private final int n;
        private final IntRef idx;
        private int i;
        private int cur;
        private boolean started;

        Iterator(byte[] data, int n) {
            this.data = data;
            this.n = n;
            this.idx = new IntRef(0);
            this.i = 0;
            this.cur = 0;
            this.started = false;
        }

        public boolean hasNext() { return i < n; }

        public int next() {
            if (!hasNext()) throw new IllegalStateException("No more elements");
            int v = Varint.readUnsigned(data, idx);
            if (!started) {
                cur = v;
                started = true;
            } else {
                cur += v;
            }
            i++;
            return cur;
        }
    }
}
