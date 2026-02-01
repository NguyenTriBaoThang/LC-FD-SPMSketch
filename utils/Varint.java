package utils;

import java.io.ByteArrayOutputStream;

public final class Varint {
    private Varint() {}

    public static void writeUnsigned(ByteArrayOutputStream out, int value) {
        long v = value & 0xFFFFFFFFL;
        while ((v & ~0x7FL) != 0) {
            out.write((int)((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int)v);
    }

    public static int readUnsigned(byte[] data, IntRef idx) {
        long result = 0;
        int shift = 0;
        while (idx.value < data.length) {
            int b = data[idx.value++] & 0xFF;
            result |= (long)(b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 35) throw new IllegalArgumentException("Varint too long");
        }
        return (int) result;
    }

    public static final class IntRef {
        public int value;
        public IntRef(int v) { this.value = v; }
    }
}
