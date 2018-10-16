
import io.anuke.rudp.handlers.OrderedPacketHandler;
import io.anuke.rudp.handlers.PacketHandler;
import io.anuke.rudp.rudp.RUDPClient;
import io.anuke.rudp.rudp.RUDPServer;
import io.anuke.rudp.utils.NetUtils;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;

public class LocalServClientTest {
	
	public static class ServerPHandler implements PacketHandler{

	}
	
	public static class ClientPHandler extends OrderedPacketHandler {

		@Override
		public void handlePacket(byte[] data) {
			System.out.println(NetUtils.asHexString(data));
		}
		
		@Override
		public void onDisconnected(String reason, boolean local) {
			super.onDisconnected(reason, local);
			System.out.println("DC reason: " + reason);
		}
	}

	@Test
	public void testConnection() throws IOException{
		RUDPServer server = new RUDPServer(1111);
		server.setPacketHandler(new ServerPHandler());
		server.start();

		RUDPClient client = new RUDPClient(InetAddress.getByName("localhost"), 1111);
		client.setPacketHandler(new ClientPHandler());
		client.connect();
		
		server.getConnectedClients().get(0).sendPacket(new byte[]{0});
		
		client.disconnect();
		client.connect();
		
		server.getConnectedClients().get(0).sendPacket(new byte[]{0});
		server.getConnectedClients().get(0).sendReliablePacket(new byte[]{0});
		
		client.disconnect();
		client.connect();
		
		client.disconnect();
		server.stop();
	}
}
