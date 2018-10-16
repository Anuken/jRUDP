package io.anuke.rudp.utils;

import io.anuke.rudp.rudp.RudpPacket;

import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Priority queue wrapper for packet definitions
 *
 * @author iGoodie
 */
public class PacketQueue{
    private PriorityQueue<RudpPacket> packetQueue;

    public PacketQueue(){
        packetQueue = new PriorityQueue<>((a, b) -> Integer.compare(a.sequenceNum, b.sequenceNum));
    }

    public void enqueue(RudpPacket packet){
        packetQueue.add(packet);
    }

    public RudpPacket dequeue(){
        return packetQueue.isEmpty() ? null : packetQueue.remove();
    }

    public RudpPacket peek(){
        return packetQueue.peek();
    }

    public int size(){
        return packetQueue.size();
    }

    public boolean isEmpty(){
        return packetQueue.isEmpty();
    }

    public Iterator<RudpPacket> iterator(){
        return packetQueue.iterator();
    }
}
