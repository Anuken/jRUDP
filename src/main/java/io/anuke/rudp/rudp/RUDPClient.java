package io.anuke.rudp.rudp;

import io.anuke.rudp.RUDPConstants;
import io.anuke.rudp.RUDPConstants.PacketType;
import io.anuke.rudp.handlers.PacketHandler;
import io.anuke.rudp.utils.NetUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

public class RUDPClient{ //TODO remove use of ByteBuffers and use functions instead

    public ConnectionState state = ConnectionState.STATE_DISCONNECTED;
    InetAddress address;
    int port;
    long lastPacketReceiveTime;
    short sequenceReliable = 0;
    short sequenceUnreliable = 0;
    short lastPingSeq = 0;
    int sent, sentReliable;
    int received, receivedReliable;
    private ClientType type = ClientType.NORMAL_CLIENT;
    private RUDPServer server;
    private DatagramSocket socket;
    private PacketHandler packetHandler;
    private Thread reliableThread;
    private Thread receiveThread;
    private Thread pingThread;
    private LinkedHashMap<Short, Long> packetsReceived = new LinkedHashMap<>();
    private final List<ReliablePacket> packetsSent = Collections.synchronizedList(new ArrayList<>());
    private int latency = 400;
    private RUDPClient instance = this;

    public RUDPClient(InetAddress address, int port){
        this.address = address;
        this.port = port;
    }

    RUDPClient(InetAddress clientAddress, int clientPort, RUDPServer rudpServer, PacketHandler handler){
        this.address = clientAddress;
        this.port = clientPort;
        this.server = rudpServer;
        this.type = ClientType.SERVER_CHILD;
        this.sentReliable = 0;
        this.sent = 0;
        this.packetHandler = handler;

        lastPacketReceiveTime = System.currentTimeMillis();

        state = ConnectionState.STATE_CONNECTING;
    }

    /* Getter & Setters */
    public InetAddress getAddress(){
        return address;
    }

    public int getPort(){
        return port;
    }

    public boolean isConnected(){
        return state == ConnectionState.STATE_CONNECTED;
    }

    public void setPacketHandler(PacketHandler handler){
        this.packetHandler = handler;
    }

    public int getLatency(){
        return latency;
    }

    public int getSent(){
        return sent;
    }

    public int getSentReliable(){
        return sentReliable;
    }

    public int getReceived(){
        return received;
    }

    public int getReceivedReliable(){
        return receivedReliable;
    }

    private short getReliablePacketSequence(){
        short prev = sequenceReliable;
        sequenceReliable = NetUtils.shortIncrement(sequenceReliable);
        return prev;
    }

    private short getUnreliablePacketSequence(){
        short prev = sequenceUnreliable;
        sequenceUnreliable = NetUtils.shortIncrement(sequenceUnreliable);
        return prev;
    }

    public void connect() throws IOException{
        System.out.println("[RUDPClient] Connecting to UDP port " + port + "...");
        if(state == ConnectionState.STATE_CONNECTED){
            System.out.println("[RUDPClient] Client already connected !");
            return;
        }
        if(state == ConnectionState.STATE_CONNECTING){
            System.out.println("[RUDPClient] Client already connecting !");
            return;
        }
        if(state == ConnectionState.STATE_DISCONNECTING){
            System.out.println("[RUDPClient] Client currently disconnecting !");
            return;
        }

        socket = new DatagramSocket();
        socket.setSoTimeout(RUDPConstants.CLIENT_TIMEOUT_TIME);

        lastPacketReceiveTime = System.currentTimeMillis();

        state = ConnectionState.STATE_CONNECTING;
        try{
            //Send handshake packet
            byte[] handshakePacket = new byte[9];
            handshakePacket[0] = PacketType.HANDSHAKE_START;
            NetUtils.writeBytes(handshakePacket, 1, RUDPConstants.VERSION_MAJOR);
            NetUtils.writeBytes(handshakePacket, 5, RUDPConstants.VERSION_MINOR);
            sendPacketRaw(handshakePacket);

            //Receive handshake response packet
            byte[] buffer = new byte[8196];
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, address, port);
            socket.receive(datagramPacket);
            byte[] data = new byte[datagramPacket.getLength()];
            System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), data, 0, datagramPacket.getLength());

            //Handle handshake response packet
            if(data[0] != PacketType.HANDSHAKE_OK){

                state = ConnectionState.STATE_DISCONNECTED;
                byte[] dataText = new byte[data.length - 1];
                System.arraycopy(data, 1, dataText, 0, dataText.length);
                throw new IOException("Unable to connect: " + new String(dataText, StandardCharsets.UTF_8));

            }else{

                state = ConnectionState.STATE_CONNECTED;
                initReceiveThread();
                initPingThread();
                initRelyThread();

                reliableThread.start();
                receiveThread.start();
                pingThread.start();

                System.out.println("[RUDPClient] Connected !");

            }
        }catch(IOException e){
            state = ConnectionState.STATE_DISCONNECTED;
            throw e;
        }
    }

    public void disconnect(){
        disconnect("Disconnected Manually");
    }

    public void disconnect(String reason){
        if(state == ConnectionState.STATE_DISCONNECTED || state == ConnectionState.STATE_DISCONNECTING) return;
        byte[] reponse = reason.getBytes(StandardCharsets.UTF_8);

        if(type == ClientType.SERVER_CHILD){
            sendReliablePacket(PacketType.DISCONNECT_FROM_SERVER, reponse);
            state = ConnectionState.STATE_DISCONNECTING;
        }
        if(type == ClientType.NORMAL_CLIENT){
            sendPacket(PacketType.DISCONNECT_FROM_CLIENT, reponse);
            state = ConnectionState.STATE_DISCONNECTED;
            socket.close();
        }
        if(packetHandler != null) packetHandler.onDisconnected(reason, true);
    }

    public void sendReliablePacket(byte[] data){
        sendReliablePacket(PacketType.RELIABLE, data);
    }

    public void sendReliablePacket(byte packetType, byte[] data){
        if(state == ConnectionState.STATE_DISCONNECTED) return;
        byte[] packet = new byte[data.length + 3];
        long timeMS = System.currentTimeMillis();
        short seq = getReliablePacketSequence();

        packet[0] = packetType;
        NetUtils.writeBytes(packet, 1, seq);
        System.arraycopy(data, 0, packet, 3, data.length);
        if(type == ClientType.SERVER_CHILD){
            server.sendPacket(packet, address, port);
        }else{
            DatagramPacket dpacket = new DatagramPacket(packet, packet.length, address, port);
            try{
                socket.send(dpacket);
            }catch(IOException e){
                e.printStackTrace();
                return;
            }
        }
        synchronized(packetsSent){
            packetsSent.add(new ReliablePacket(seq, timeMS, packet));
        }
        sentReliable++;
    }

    public void sendPacket(byte[] data){
        sendPacket(PacketType.UNRELIABLE, data);
    }

    public void requestRemoteStats(){
        sendPacket(PacketType.PACKETSSTATS_REQUEST, new byte[0]);
    }

    void initialize(){
        initRelyThread();
        reliableThread.start();
        state = ConnectionState.STATE_CONNECTED;
        packetHandler.onConnection();
    }

    private void initReceiveThread(){
        receiveThread = new Thread(() -> {
            while(state == ConnectionState.STATE_CONNECTED){
                byte[] buffer = new byte[RUDPConstants.RECEIVE_MAX_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try{
                    socket.receive(packet);
                }catch(SocketTimeoutException e){
                    state = ConnectionState.STATE_DISCONNECTED;
                    disconnected("Connection timed out");
                    return;
                }catch(IOException e){
                    if(state == ConnectionState.STATE_DISCONNECTED) return;
                    System.err.println("[RUDPClient] An error as occured while receiving a packet: ");
                    e.printStackTrace();
                }
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                try{
                    handlePacket(data);
                }catch(Exception e){
                    System.err.print("[RUDPClient] An error occured while handling packet:");
                    e.printStackTrace();
                }
                packet.setLength(RUDPConstants.RECEIVE_MAX_SIZE);
            }
        }, "RUDPClient receive thread");
    }

    private void initPingThread(){
        pingThread = new Thread(() -> {
            try{
                while(state == ConnectionState.STATE_CONNECTED){
                    byte[] pingPacket = new byte[8];
                    NetUtils.writeBytes(pingPacket, 0, System.currentTimeMillis());
                    sendPacket(PacketType.PING_REQUEST, pingPacket);

                    Thread.sleep(RUDPConstants.PING_INTERVAL);
                }
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }, "RUDPClient ping thread");
    }

    private void initRelyThread(){
        reliableThread = new Thread(() -> {
            try{
                while(state == ConnectionState.STATE_CONNECTING || state == ConnectionState.STATE_CONNECTED || (state == ConnectionState.STATE_DISCONNECTING && !packetsSent.isEmpty())){
                    synchronized(packetsSent){
                        long currentMS = System.currentTimeMillis();
                        long minMS = currentMS + (latency * 2);
                        int i = 0;
                        while(i < packetsSent.size()){
                            ReliablePacket rpacket = packetsSent.get(i);

                            if(rpacket.dateMS + RUDPConstants.PACKET_TIMEOUT_TIME_MILLISECONDS < currentMS){
                                packetsSent.remove(i);
                                continue;
                            }
                            if(rpacket.minDateMS < currentMS){
                                rpacket.minDateMS = minMS;
                                sendPacketRaw(rpacket.data);
                            }
                            i++;
                        }
                    }

                    if(state == ConnectionState.STATE_DISCONNECTING && packetsSent.size() == 0){
                        state = ConnectionState.STATE_DISCONNECTED;
                        server.remove(this);
                        return;
                    }
                    Thread.sleep(20);
                }
                state = ConnectionState.STATE_DISCONNECTED;
                if(type == ClientType.SERVER_CHILD) server.remove(instance);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }, "RUDPClient rely thread");
    }

    void disconnected(String reason){
        state = ConnectionState.STATE_DISCONNECTED;
        if(packetHandler != null) packetHandler.onDisconnected(reason, false);
        if(type == ClientType.SERVER_CHILD) server.remove(this);
    }

    void sendPacket(byte packetType, byte[] data){
        if(state == ConnectionState.STATE_DISCONNECTED) return;
        byte[] packet = new byte[data.length + 3];
        short seq = getUnreliablePacketSequence();

        packet[0] = packetType;
        NetUtils.writeBytes(packet, 1, seq);
        System.arraycopy(data, 0, packet, 3, data.length);

        if(type == ClientType.SERVER_CHILD) server.sendPacket(packet, address, port);
        else{
            DatagramPacket dpacket = new DatagramPacket(packet, packet.length, address, port);
            try{
                socket.send(dpacket);
            }catch(IOException e){
                e.printStackTrace();
                return;
            }
        }
        sent++;
    }

    /**
     * Handles received packet assuming server won't send any empty packets. (data.len != 0)
     *
     * @param data Header and payload of received packet
     */
    void handlePacket(byte[] data){
        if((state == ConnectionState.STATE_DISCONNECTING || state == ConnectionState.STATE_DISCONNECTED) && data[0] != PacketType.RELY)
            return;

        lastPacketReceiveTime = System.currentTimeMillis(); //Assume packet received when handling started

        //Counter
        if(RUDPConstants.isPacketReliable(data[0])){

            //Send rely packet
            byte[] l = new byte[]{data[1], data[2]};
            sendPacket(PacketType.RELY, l);

            //save to received packet list
            short seq = NetUtils.asShort(data, 1);
            Long currentTime = System.currentTimeMillis();
            Long packetOverTime = currentTime + RUDPConstants.PACKET_STORE_TIME_MILLISECONDS;

            Iterator<Entry<Short, Long>> it = packetsReceived.entrySet().iterator();
            while(it.hasNext()){
                Entry<Short, Long> storedSeq = it.next();
                if(storedSeq.getKey() == seq){
                    return;
                }
                if(storedSeq.getValue() < currentTime) it.remove(); //XXX use another thread ?
            }
            receivedReliable++;
            packetsReceived.put(seq, packetOverTime);
        }else{
            received++;
        }

        if(data[0] == PacketType.RELY){
            synchronized(packetsSent){
                for(int i = 0; i < packetsSent.size(); i++){
                    if(packetsSent.get(i).seq == NetUtils.asShort(data, 3)){
                        packetsSent.remove(i);
                        return;
                    }
                }
                if(state == ConnectionState.STATE_DISCONNECTING && packetsSent.size() == 0){
                    state = ConnectionState.STATE_DISCONNECTED;
                    server.remove(this);
                }
            }
        }else if(data[0] == PacketType.PING_REQUEST){
            short seq = NetUtils.asShort(data, 1);
            if(NetUtils.sequenceGreaterThan(seq, lastPingSeq)){
                lastPingSeq = seq;
                byte[] l = new byte[]{data[3], data[4], data[5], data[6], data[7], data[8], data[9], data[10]};
                sendPacket(PacketType.PING_RESPONSE, l);//sending time received (long) // ping packet format: [IN:] CMD_PING_REPONSE seqId sendMilliseconds
            }
        }else if(data[0] == PacketType.PING_RESPONSE){
            short seq = NetUtils.asShort(data, 1);
            if(NetUtils.sequenceGreaterThan(seq, lastPingSeq)){
                lastPingSeq = seq;
                latency = (int) (System.currentTimeMillis() - NetUtils.asLong(data, 3));
                if(latency < 5) latency = 5;
            }
        }else if(data[0] == PacketType.DISCONNECT_FROM_SERVER){
            byte[] packetData = new byte[data.length - 3];
            System.arraycopy(data, 3, packetData, 0, data.length - 3);
            disconnected(new String(packetData, StandardCharsets.UTF_8));
        }else if(data[0] == PacketType.RELIABLE){

            //handle reliable packet
            if(packetHandler != null){
                try{
                    packetHandler.onPacketReceived(data, true); //pass raw packet payload
                }catch(Exception e){
                    //TODO why the heck is this simply supressed?
                    e.printStackTrace();
                }
            }
        }else if(data[0] == PacketType.PACKETSSTATS_REQUEST){
            byte[] packet = new byte[17];
            NetUtils.writeBytes(packet, 0, sent + 1); // Add one to count the current packet
            NetUtils.writeBytes(packet, 4, sentReliable);
            NetUtils.writeBytes(packet, 8, received);
            NetUtils.writeBytes(packet, 12, receivedReliable);
            sendPacket(PacketType.PACKETSSTATS_RESPONSE, packet);
        }else if(data[0] == PacketType.PACKETSSTATS_RESPONSE){
            int sentRemote = NetUtils.asInt(data, 3);
            int sentRemoteR = NetUtils.asInt(data, 7);
            int receivedRemote = NetUtils.asInt(data, 11);
            int receivedRemoteR = NetUtils.asInt(data, 15);
            packetHandler.onRemoteStatsReturned(sentRemote, sentRemoteR, receivedRemote, receivedRemoteR);
        }else if(packetHandler != null){
            try{
                packetHandler.onPacketReceived(data, false); //pass raw packet payload
            }catch(Exception e){
                //TODO why the heck is this simply supressed?
                e.printStackTrace();
            }
        }
    }

    private void sendPacketRaw(byte[] data){
        if(state == ConnectionState.STATE_DISCONNECTED) return;
        if(type == ClientType.SERVER_CHILD) server.sendPacket(data, address, port);
        else{
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            try{
                socket.send(packet);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private class ReliablePacket{
        private long dateMS;
        private long minDateMS;
        private byte[] data;
        private short seq;

        public ReliablePacket(short seq, long dateMS, byte[] data){
            this.dateMS = dateMS;
            this.minDateMS = dateMS + (latency * 2);
            this.data = data;
            this.seq = seq;
        }
    }
}
