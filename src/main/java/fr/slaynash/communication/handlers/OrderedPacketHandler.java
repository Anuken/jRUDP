package fr.slaynash.communication.handlers;

import fr.slaynash.communication.rudp.Packet;
import fr.slaynash.communication.utils.NetUtils;
import fr.slaynash.communication.utils.PacketQueue;

public abstract class OrderedPacketHandler implements PacketHandler{

    protected PacketQueue reliableQueue = new PacketQueue();
    protected short lastHandledSeq = -1;

    public abstract void handlePacket(byte[] data);

    @Override
    public void onConnection(){
    }

    @Override
    public void onDisconnected(String reason, boolean local){
        reliableQueue = new PacketQueue();
        lastHandledSeq = Short.MAX_VALUE;
    }

    @Override
    public void onRemoteStatsReturned(int sentRemote, int sentRemoteR, int receivedRemote, int receivedRemoteR){
    }

    @Override
    public void onPacketReceived(byte[] data, boolean local){
        Packet packet = new Packet(data); //Parse received packet
        short expectedSeq = NetUtils.shortIncrement(lastHandledSeq); //last + 1

        if(NetUtils.sequenceGreaterThan(lastHandledSeq, packet.sequenceNum)){ // (last > received) == (received < last)
            return; // Drop the packet, because we already handled it
        }

        //Received an unexpected packet? Enqueue and pass
        if(packet.sequenceNum != expectedSeq){
            reliableQueue.enqueue(packet);
            return;
        }

        // Handle expected packet
        handlePacket(packet.rawPayload);
        lastHandledSeq = packet.sequenceNum;
        expectedSeq = NetUtils.shortIncrement(lastHandledSeq);

        // Handle every waiting packet
        while(!reliableQueue.isEmpty() && reliableQueue.peek().sequenceNum == expectedSeq){
            packet = reliableQueue.dequeue();
            handlePacket(packet.rawPayload);
            lastHandledSeq = expectedSeq;
            expectedSeq = NetUtils.shortIncrement(lastHandledSeq);
        }
    }
}
