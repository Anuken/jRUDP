package io.anuke.rudp.utils;

/**
 * Fast values to bytes conversion
 */
public abstract class NetUtils{

    public static short asShort(byte[] buffer, int offset){
        return (short) (
        ((buffer[offset] & 0xFF) << 8)
        | ((buffer[offset + 1] & 0xFF))
        );
    }

    public static int asInt(byte[] buffer, int offset){
        return ((buffer[offset] & 0xFF) << 24)
        | ((buffer[offset + 1] & 0xFF) << 16)
        | ((buffer[offset + 2] & 0xFF) << 8)
        | ((buffer[offset + 3] & 0xFF));
    }

    public static long asLong(byte[] buffer, int offset){
        return ((long) (buffer[offset] & 0xFF) << 56)
        | ((long) (buffer[offset + 1] & 0xFF) << 48)
        | ((long) (buffer[offset + 2] & 0xFF) << 40)
        | ((long) (buffer[offset + 3] & 0xFF) << 32)
        | ((long) (buffer[offset + 4] & 0xFF) << 24)
        | ((long) (buffer[offset + 5] & 0xFF) << 16)
        | ((long) (buffer[offset + 6] & 0xFF) << 8)
        | ((long) (buffer[offset + 7] & 0xFF));
    }

    /* Writers */
    public static void writeBytes(byte[] buffer, int offset, short num){
        buffer[offset] = (byte) (num >> 8);
        buffer[offset + 1] = (byte) (num);
    }

    public static void writeBytes(byte[] buffer, int offset, int num){
        buffer[offset] = (byte) (num >> 24);
        buffer[offset + 1] = (byte) (num >> 16);
        buffer[offset + 2] = (byte) (num >> 8);
        buffer[offset + 3] = (byte) (num);
    }

    public static void writeBytes(byte[] buffer, int offset, long num){
        buffer[offset] = (byte) (num >> 56);
        buffer[offset + 1] = (byte) (num >> 48);
        buffer[offset + 2] = (byte) (num >> 40);
        buffer[offset + 3] = (byte) (num >> 32);
        buffer[offset + 4] = (byte) (num >> 24);
        buffer[offset + 5] = (byte) (num >> 16);
        buffer[offset + 6] = (byte) (num >> 8);
        buffer[offset + 7] = (byte) (num);
    }

    public static String asHexString(byte[] source){
        if(source.length == 0) return "";
        StringBuilder sb = new StringBuilder("0x");

        for(int i = 0; i < source.length; i++){
            sb.append(String.format("%02X", source[i]));
            if(i != source.length - 1) sb.append("_");
        }

        return sb.toString().trim();
    }

    public static boolean sequenceGreaterThan(short s1, short s2){
        return ((s1 > s2) && (s1 - s2 <= 32768)) ||
        ((s1 < s2) && (s2 - s1 > 32768));
    }

    public static short shortIncrement(short num){
        if(num == Short.MAX_VALUE) return Short.MIN_VALUE;
        return (short) (num + 1);
    }
}
