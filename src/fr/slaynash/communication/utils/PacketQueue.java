package fr.slaynash.communication.utils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import fr.slaynash.communication.rudp.Packet;

/**
 * Priority queue wrapper for packet definitions
 * @author iGoodie
 */
public class PacketQueue {
	private PriorityQueue<Packet> packetQueue;
	
	public PacketQueue() {
		packetQueue = new PriorityQueue<>((a, b) -> Integer.compare(a.getHeader().getSequenceNo(), b.getHeader().getSequenceNo()));
	}

	public void enqueue(Packet packet) {
		packetQueue.add(packet);
	}

	public Packet dequeue() {
		return packetQueue.isEmpty() ? null : packetQueue.remove();
	}

	public Packet peek() {
		return packetQueue.peek();
	}

	public int size() {
		return packetQueue.size();
	}

	public boolean isEmpty() {
		return packetQueue.isEmpty();
	}

	public Iterator<Packet> iterator() {
		return packetQueue.iterator();
	}
}
