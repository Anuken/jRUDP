package io.anuke.rudp.utils;

import io.anuke.rudp.rudp.Packet;

import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Priority queue wrapper for packet definitions
 *
 * @author iGoodie
 */
public class PacketQueue{
    private PriorityQueue<Packet> packetQueue;

    public PacketQueue(){
        packetQueue = new PriorityQueue<>((a, b) -> Integer.compare(a.getHeader().getSequenceNo(), b.getHeader().getSequenceNo()));
    }

    public void enqueue(Packet packet){
        packetQueue.add(packet);
    }

    public Packet dequeue(){
        return packetQueue.isEmpty() ? null : packetQueue.remove();
    }

    public Packet peek(){
        return packetQueue.peek();
    }

    public int size(){
        return packetQueue.size();
    }

    public boolean isEmpty(){
        return packetQueue.isEmpty();
    }

    public Iterator<Packet> iterator(){
        return packetQueue.iterator();
    }
}
