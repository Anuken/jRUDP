package io.anuke.rudp.handlers;

public interface PacketHandler{

    /**Called when a client connects.*/
    default void onConnection(){}

    /**Called when the client disconnects.
     *
     * @param reason The string reason. TODO remove
     * @param local whether the disconnection was caused by a disconnect() locally
     */
    default void onDisconnected(String reason, boolean local){}

    /**Called when a packet is recieved. If the packet type is reliable, reliability must be handled by the listener!*/
    default void onPacketReceived(byte[] data, boolean reliable){}

    /**???*/
    default void onRemoteStatsReturned(int sentRemote, int sentRemoteR, int receivedRemote, int receivedRemoteR){}
}
