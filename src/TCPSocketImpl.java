import java.net.DatagramPacket;
import java.net.InetAddress;

public class TCPSocketImpl extends TCPSocket {
    private EnhancedDatagramSocket UDPSocket;
    private InetAddress serverIp;
    private int serverPort;

    private final long SSThreshold = 0;
    private final long windowSize = 0;
    private long seq = 100;

    public enum State {
        NONE,  // client
        SYN_SENT,  // client
        SYN_RECEIVED,  // server
        ESTABLISHED  // both sides
    }

    private State state;

    private TCPSocketImpl(String ip, int port, State state) throws Exception {
        super(ip, port);
        this.UDPSocket = new EnhancedDatagramSocket(0);
        this.serverIp = InetAddress.getByName(ip);
        this.serverPort = port;
        this.state = state;
        System.err.println("Client is up on port " + this.UDPSocket.getLocalPort()
                + " and is connected to " + this.serverIp + ":" + this.serverPort + ".");
    }

    public TCPSocketImpl(String ip, int port) throws Exception {  // for client
        this(ip, port, State.NONE);
        TCPPacket packet = new TCPPacket(seq++, (long) (0), false, true, null);
        UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                this.serverIp, this.serverPort));
        this.state = State.SYN_SENT;
        System.err.println("Handshaking: sent 1/3");
        DatagramPacket UDPPacket = null;
        TCPPacket req = null;
        while (req == null || !(req.getSYN() && req.getACK() && req.getAcknowledgementNumber() == this.seq)) {
            if (req != null)
                System.err.println("Invalid TCP Package");
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            UDPPacket = new DatagramPacket(data, data.length);
            UDPSocket.receive(UDPPacket);
            req = new TCPPacket(data);
        }
        this.serverPort = UDPPacket.getPort();
        System.err.println("Client is connected to " + this.serverIp + ":" + this.serverPort + ".");
        this.state = State.ESTABLISHED;
        System.err.println("Handshaking: received 2/3");
        TCPPacket res = new TCPPacket(seq++, req.getSequenceNumber() + 1,
                true, false, null);
        this.UDPSocket.send(new DatagramPacket(res.toUDPData(), res.getBytesNumber(), this.serverIp, this.serverPort));
        System.err.println("Handshaking: sent 3/3");
    }

    public TCPSocketImpl(String ip, int port, TCPPacket handshakingReq) throws Exception {  // for server
        this(ip, port, State.SYN_RECEIVED);
        TCPPacket res = new TCPPacket(seq++, handshakingReq.getSequenceNumber() + 1,
                true, true, null);
        this.UDPSocket.send(new DatagramPacket(res.toUDPData(), res.getBytesNumber(), this.serverIp, this.serverPort));
        System.err.println("Handshaking: sent 2/3");
        DatagramPacket UDPPacket = null;
        TCPPacket req = null;
        while (req == null || !(!req.getSYN() && req.getACK() && req.getAcknowledgementNumber() == this.seq)) {
            if (req != null)
                System.err.println("Invalid TCP Package");
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            UDPPacket = new DatagramPacket(data, data.length);
            UDPSocket.receive(UDPPacket);
            req = new TCPPacket(data);
        }
        this.state = State.ESTABLISHED;
        System.err.println("Handshaking: received 3/3");
    }

    @Override
    public void send(String pathToFile) throws Exception {
        TCPPacket packet = new TCPPacket(seq++, pathToFile.getBytes());
        this.UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                this.serverIp, this.serverPort));
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
        this.UDPSocket.receive(new DatagramPacket(data, data.length));
        TCPPacket packet = new TCPPacket(data);
        System.out.println("> " + new String(packet.getData()));
    }

    @Override
    public void close() throws Exception {
        this.UDPSocket.close();
        System.err.println("Client is shutting down.");
    }

    @Override
    public long getSSThreshold() {
        return SSThreshold;
    }

    @Override
    public long getWindowSize() {
        return windowSize;
    }
}
