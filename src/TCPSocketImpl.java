import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;

public class TCPSocketImpl extends TCPSocket {
    private EnhancedDatagramSocket UDPSocket;
    private InetAddress serverIp;
    private int serverPort;

    private final long SSThreshold = 0;
    private final long windowSize = 1;
    private long seq = 100;
    private long expectedSeq;

    private ArrayList<TCPPacket> window = new ArrayList<>();

    private AckThread ackThread;

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
        ConsoleLog.connectionLog(String.format("Client is up on port %d and is connected to %s:%d.",
                this.UDPSocket.getLocalPort(), this.serverIp, this.serverPort));
        ackThread = new AckThread(this, this.UDPSocket);
    }

    public TCPSocketImpl(String ip, int port) throws Exception {  // for client
        this(ip, port, State.NONE);
        TCPPacket packet = new TCPPacket(seq++, (long) (0), false, true, null);
        UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                this.serverIp, this.serverPort));
        this.state = State.SYN_SENT;
        ConsoleLog.handshakingLog("Handshaking: sent 1/3");
        DatagramPacket UDPPacket = null;
        TCPPacket req = null;
        while (req == null || !(req.getSYN() && req.getACK()
                && req.getAcknowledgementNumber() == this.seq)) {
            if (req != null)
                System.err.println("Invalid TCP Package");
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            UDPPacket = new DatagramPacket(data, data.length);
            UDPSocket.receive(UDPPacket);
            req = new TCPPacket(data);
        }
        this.serverPort = UDPPacket.getPort();
        ConsoleLog.connectionLog(String.format("Client now is connected to %s:%d.",
                this.serverIp, this.serverPort));
        this.expectedSeq = req.getSequenceNumber() + 1;
        this.state = State.ESTABLISHED;
        ConsoleLog.handshakingLog("Handshaking: received 2/3");
        TCPPacket res = new TCPPacket(seq++, req.getSequenceNumber() + 1,
                true, false, null);
        this.UDPSocket.send(new DatagramPacket(res.toUDPData(), res.getBytesNumber(),
                this.serverIp, this.serverPort));
        ConsoleLog.handshakingLog("Handshaking: sent 3/3");
    }

    public TCPSocketImpl(String ip, int port, TCPPacket handshakingReq) throws Exception {  // for server
        this(ip, port, State.SYN_RECEIVED);
        TCPPacket res = new TCPPacket(seq++, handshakingReq.getSequenceNumber() + 1,
                true, true, null);
        this.UDPSocket.send(new DatagramPacket(res.toUDPData(), res.getBytesNumber(),
                this.serverIp, this.serverPort));
        ConsoleLog.handshakingLog("Handshaking: sent 2/3");
        TCPPacket req = null;
        while (req == null || !(!req.getSYN() && req.getACK()
                && req.getAcknowledgementNumber() == this.seq)) {
            if (req != null)
                System.err.println("Invalid TCP Package");
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            DatagramPacket UDPPacket = new DatagramPacket(data, data.length);
            UDPSocket.receive(UDPPacket);
            req = new TCPPacket(data);
        }
        this.expectedSeq = req.getSequenceNumber() + 1;
        this.state = State.ESTABLISHED;
        ConsoleLog.handshakingLog("Handshaking: received 3/3");
    }

    private void send(byte[] data) throws Exception {
        TCPPacket packet = new TCPPacket(seq, data);
        this.UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                this.serverIp, this.serverPort));
        window.add(packet);
        ackThread.addExpectedAck(seq + packet.getDataLength() + 1);
    }

    private byte[] receive() throws Exception {
        byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes()];
        this.UDPSocket.receive(new DatagramPacket(data, data.length));
        TCPPacket req = new TCPPacket(data);
        byte[] ret = null;
        if (req.getSequenceNumber() == this.expectedSeq) {
            this.expectedSeq += req.getDataLength() + 1;
            ret = req.getData();
        } else {
            System.err.println("Invalid TCP Package");
            System.err.println("seq: " + req.getSequenceNumber() + " exp: " + this.expectedSeq);
        }
        TCPPacket res = new TCPPacket(seq++, this.expectedSeq, true, false, null);
        this.UDPSocket.send(new DatagramPacket(res.toUDPData(), res.getBytesNumber(),
                this.serverIp, this.serverPort));
        return ret;
    }

    @Override
    public void send(String pathToFile) throws Exception {
        File file = new File(pathToFile);
        BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(file));
        int sentBytes = 0;
        ackThread.start();
        while (sentBytes < file.length()) {
            byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes() - TCPPacket.dataOffsetByBytes];
            sentBytes += buffer.read(data, 0, data.length);
            ConsoleLog.fileLog(((float) sentBytes / file.length() * 100) + "%");
            while(window.size() >= windowSize); // TODO: spinlock must be replaced with sth more efficient
            this.send(data);
        }
        this.send((byte[]) null);
        ackThread.stopRunning();
        buffer.close();
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        BufferedOutputStream buffer = new BufferedOutputStream(new FileOutputStream(new File(pathToFile)));
        while (true) {
            byte[] data = this.receive();
            if (data == null)
                continue;
            if (data.length == 0)
                break;
            buffer.write(data);
        }
        buffer.close();
    }

    @Override
    public void close() throws Exception {
        this.UDPSocket.close();
        ConsoleLog.connectionLog("Client is down.");
    }

    @Override
    public long getSSThreshold() {
        return SSThreshold;
    }

    @Override
    public long getWindowSize() {
        return windowSize;
    }

    public void newAckReceived(int packetsToMove){
        if(packetsToMove > window.size()) {
            window.clear();
            return;
        }
        for(int i = 0; i < packetsToMove; i++)
            window.remove(0);
    }
}
