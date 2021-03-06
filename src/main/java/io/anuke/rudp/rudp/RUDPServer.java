package io.anuke.rudp.rudp;

import io.anuke.rudp.RUDPConstants;
import io.anuke.rudp.RUDPConstants.PacketType;
import io.anuke.rudp.handlers.PacketHandler;
import io.anuke.rudp.utils.NetUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RUDPServer{// receive buffer is bigger (4096B) and client packet is dynamic (<4096B (reliable) / ~21B or ~45B (avoidable))
    //Packet format:
    //
    //data:							 type:	 	size:
    //packet type					[byte]		  1
    //sequence id					[short]	 	  2
    //payload						[byte[]]	<4094

    private int port;
    private DatagramSocket datagramSocket;

    private Thread serverThread;
    private Thread clientDropHandlerThread;

    private boolean running = false;
    private boolean stopping = false;
    private PacketHandler handler;
    private final List<RUDPClient> clients = new ArrayList<>();
    private final ConcurrentHashMap<Integer, RUDPClient> clientMap = new ConcurrentHashMap<>();
    private int lastClientID;

    public RUDPServer(int port) throws SocketException{
        this.port = port;
        datagramSocket = new DatagramSocket(port);

        startServerThread();

        initClientDropHandler();
    }

    public int getPort(){
        return port;
    }

    public boolean isRunning(){
        return running;
    }

    public RUDPClient getClient(int id){
        return clientMap.get(id);
    }

    public List<RUDPClient> getConnectedClients(){
        synchronized(clients){
            return new ArrayList<>(clients);
        }
    }

    public RUDPClient getClient(InetAddress address, int port){
        for(RUDPClient c : clients){
            if(c.address.equals(address) && c.port == port){
                return c;
            }
        }
        return null;
    }

    public void setPacketHandler(PacketHandler handler){
        this.handler = handler;
    }

    public void start(){
        if(running) return;
        running = true;

        serverThread.start();
        clientDropHandlerThread.start();

        System.out.println("[RUDPServer] Server started on UDP port " + port);
    }

    public void stop(){
        System.out.println("Stopping server...");
        synchronized(clients){
            stopping = true;
            for(RUDPClient client : clients){
                client.disconnect("Server shutting down");
            }
        }
        clients.clear();
        running = false;
        datagramSocket.close();
    }

    public void kick(int id, String reason){
        synchronized(clients){
            RUDPClient clientToRemove = clients.get(id);
            byte[] reasonB = reason.getBytes(StandardCharsets.UTF_8);
            clientToRemove.sendPacket(PacketType.DISCONNECT_FROM_SERVER, reasonB);
            clientToRemove.state = ConnectionState.STATE_DISCONNECTED;
            clients.remove(clientToRemove);
        }
    }

    /* Helper Methods */
    private void handlePacket(byte[] data, InetAddress clientAddress, int clientPort){
        //Check if packet is not empty
        if(data.length == 0){
            System.out.println("[RUDPServer] Empty packet received");
            return;
        }

        //check if packet is an handshake packet
        if(data[0] == PacketType.HANDSHAKE_START){
            //If client is valid, add it to the list and initialize it

            if(stopping){
                byte[] error = "Server closing".getBytes(StandardCharsets.UTF_8);
                byte[] reponse = new byte[error.length + 1];
                reponse[0] = PacketType.HANDSHAKE_ERROR;
                System.arraycopy(error, 0, reponse, 1, error.length);
                sendPacket(reponse, clientAddress, clientPort);
            }else if(NetUtils.asInt(data, 1) == RUDPConstants.VERSION_MAJOR && NetUtils.asInt(data, 5) == RUDPConstants.VERSION_MINOR){//version check

                sendPacket(new byte[]{PacketType.HANDSHAKE_OK}, clientAddress, clientPort);

                final RUDPClient rudpclient = new RUDPClient(clientAddress, clientPort, this, handler);
                rudpclient.setID(lastClientID++);
                synchronized(clients){
                    clients.add(rudpclient);
                    clientMap.put(rudpclient.getID(), rudpclient);
                }
                System.out.println("[RUDPServer] Added new client !");
                System.out.println("[RUDPServer] Initializing client...");
                new Thread(rudpclient::initialize, "RUDP Client init thread").start();
                return;

            }else{
                //Else send HANDSHAKE_ERROR "BAD_VERSION"

                byte[] error = "Bad version !".getBytes(StandardCharsets.UTF_8);
                byte[] reponse = new byte[error.length + 1];
                reponse[0] = PacketType.HANDSHAKE_ERROR;
                System.arraycopy(error, 0, reponse, 1, error.length);
                sendPacket(reponse, clientAddress, clientPort);

            }
        }

        //handle packet in ClientRUDP
        RUDPClient clientToRemove = null;
        for(RUDPClient client : clients){
            if(Arrays.equals(client.address.getAddress(), clientAddress.getAddress()) && client.port == clientPort){

                if(data[0] == PacketType.DISCONNECT_FROM_CLIENT){
                    byte[] reason = new byte[data.length - 3];
                    System.arraycopy(data, 3, reason, 0, reason.length);

                    client.disconnected(new String(reason, StandardCharsets.UTF_8));
                    clientToRemove = client;
                }else{
                    client.handlePacket(data);
                    return;
                }

                break;
            }
        }
        if(clientToRemove != null){
            remove(clientToRemove);
        }
    }

    protected void sendPacket(byte[] data, InetAddress address, int port){
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        packet.setData(data);
        try{
            datagramSocket.send(packet);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    void remove(RUDPClient client){
        synchronized(clients){
            clients.remove(client);
            clientMap.remove(client.getID());
        }
    }

    private void startServerThread(){
        serverThread = new Thread(() -> {
            while(running){
                byte[] buffer = new byte[RUDPConstants.RECEIVE_MAX_SIZE];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);

                try{
                    datagramSocket.receive(datagramPacket);
                }catch(IOException e){
                    if(running){
                        System.err.println("[RUDPServer] An error as occured while receiving a packet: ");
                        e.printStackTrace();
                    }
                }
                if(!running) break;
                byte[] data = new byte[datagramPacket.getLength()];
                System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), data, 0, datagramPacket.getLength());

                handlePacket(data, datagramPacket.getAddress(), datagramPacket.getPort());
                datagramPacket.setLength(RUDPConstants.RECEIVE_MAX_SIZE);
            }
        }, "RUDPServer packets receiver");
    }

    private void initClientDropHandler(){
        clientDropHandlerThread = new Thread(() -> {
            try{
                while(running){
                    synchronized(clients){
                        long maxMS = System.currentTimeMillis() - RUDPConstants.CLIENT_TIMEOUT_TIME_MILLISECONDS;
                        int i = 0;
                        while(i < clients.size()){
                            RUDPClient client = clients.get(i);
                            if(client.lastPacketReceiveTime < maxMS){
                                client.disconnected("Connection timed out");
                            }else i++;
                        }
                    }
                    Thread.sleep(250);
                }
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }, "RUDPServer client drop handler");
    }
}
