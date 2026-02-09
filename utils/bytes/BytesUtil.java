package com.utils.bytes;


public class BytesUtil {

    private static final byte[] bytes = new byte[4];

    public static byte[] int2bytes(int key) {
        bytes[3] = (byte) (key & 0xff);
        bytes[2] = (byte) ((key >> 8) & 0xff);
        bytes[1] = (byte) ((key >> 16) & 0xff);
        bytes[0] = (byte) ((key >> 24) & 0xff);

        return bytes;
    }
}
