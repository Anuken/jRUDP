package io.anuke.rudp.rudp;

import io.anuke.rudp.RUDPConstants;
import io.anuke.rudp.utils.NetUtils;

public final class RudpPacket{
    public static final int HEADER_SIZE = 3; //bytes

    public final boolean isReliable;
    public final short sequenceNum;
    public final byte[] rawPayload;

    public RudpPacket(byte[] data){
        isReliable = RUDPConstants.isPacketReliable(data[0]);
        sequenceNum = NetUtils.asShort(data, 1);

        rawPayload = new byte[data.length - HEADER_SIZE];
        System.arraycopy(data, HEADER_SIZE, rawPayload, 0, rawPayload.length);
    }
}
