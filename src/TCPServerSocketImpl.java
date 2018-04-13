import java.net.DatagramPacket;

public class TCPServerSocketImpl extends TCPServerSocket {
    EnhancedDatagramSocket UDPSocket;

    private long seq = 100;

    public TCPServerSocketImpl(int port) throws Exception {
        super(port);
        this.UDPSocket = new EnhancedDatagramSocket(port);
        System.err.println("Server is listening on port " + this.UDPSocket.getLocalPort() + ".");
    }

    @Override
    public TCPSocket accept() throws Exception {
        DatagramPacket UDPPacket = null;
        TCPPacket req = null;
        while (req == null || !(req.getSYN() && !req.getACK())) {
            if (req != null)
                System.err.println("Invalid TCP Package");
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            UDPPacket = new DatagramPacket(data, data.length);
            this.UDPSocket.receive(UDPPacket);
            req = new TCPPacket(data);
        }
        System.err.println("Handshaking: received 1/3");
        return new TCPSocketImpl(UDPPacket.getAddress().getHostAddress(), UDPPacket.getPort(), req);
    }

    @Override
    public void close() throws Exception {
        this.UDPSocket.close();
        System.err.println("Server is shutting down.");
    }
}
