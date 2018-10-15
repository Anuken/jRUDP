package fr.slaynash.communication.rudp;

import fr.slaynash.communication.RUDPConstants;
import fr.slaynash.communication.utils.NetUtils;

/**
 * Simple packet definition to be extended by other packet types
 *
 * @author iGoodie
 */
public abstract class Packet{
    public static final int HEADER_SIZE = 3; //bytes

    private PacketHeader header = new PacketHeader();
    private byte[] rawPayload;

    public Packet(byte[] data){
        //Parse header
        header.isReliable = RUDPConstants.isPacketReliable(data[0]);
        header.sequenceNum = NetUtils.asShort(data, 1);

        //Parse payload
        rawPayload = new byte[data.length - HEADER_SIZE];
        System.arraycopy(data, HEADER_SIZE, rawPayload, 0, rawPayload.length);
    }

    public PacketHeader getHeader(){
        return header;
    }

    public byte[] getRawPayload(){
        return rawPayload;
    }

    @Override
    public String toString(){
        return "Packet{" +
        "header=" + header +
        ", rawPayload=[" + rawPayload.length + " bytes]" +
        '}';
    }

    public static class PacketHeader{
        private boolean isReliable = false;
        private short sequenceNum;

        public boolean isReliable(){
            return isReliable;
        }

        public short getSequenceNo(){
            return sequenceNum;
        }

        @Override
        public String toString(){
            return "PacketHeader{" +
            "isReliable=" + isReliable +
            ", sequenceNum=" + sequenceNum +
            '}';
        }
    }
}
