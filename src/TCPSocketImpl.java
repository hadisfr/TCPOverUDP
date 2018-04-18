import java.io.*;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class TCPSocketImpl extends TCPSocket {
    private EnhancedDatagramSocket UDPSocket;
    private InetAddress serverIp;
    private int serverPort;

    private final long SSThreshold = 0;
    private long seq = 100;
    private long expectedSeq;
    private int timeout = 2000;

    private int windowSize = 1;
    private ArrayList<TCPPacket> window = new ArrayList<>();

    Timer timer = new Timer();

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

    private void send(ArrayList<TCPPacket> packets) throws Exception {
        for (TCPPacket packet : packets) {
            this.UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                    this.serverIp, this.serverPort));
        }
    }

    private void ackReceive() throws IOException {
        TCPPacket ack = null;
        while (true) {
            while (ack == null || !(ack.getACK() /*&& ack.getAcknowledgementNumber() >= this.expectedAck*/)) {
                byte[] ackData = new byte[this.UDPSocket.getPayloadLimitInBytes()];
                UDPSocket.receive(new DatagramPacket(ackData, ackData.length));
                ack = new TCPPacket(ackData);
            }
            int i;
            long currAck = ack.getAcknowledgementNumber();
            for (i = 0; i < window.size(); i++) {
                TCPPacket currPacket = window.get(i);
                if (currAck < currPacket.getSequenceNumber() + currPacket.getDataLength() + 1)
                    break;
            }
            if (i == 0)
                continue;
            timer.cancel();
            for (int j = 0; j < i; j++)
                window.remove(0);
            break;
        }
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
        ArrayList<TCPPacket> packets = new ArrayList<>();
        int sentBytes = 0;
        while (sentBytes < file.length()) {
            packets.clear();
            for (int i = 0; i < windowSize - window.size() && sentBytes < file.length(); i++) {
                byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes() - TCPPacket.dataOffsetByBytes];
                sentBytes += buffer.read(data, 0, data.length);
                TCPPacket packet = new TCPPacket(seq, data);
                packets.add(packet);
                seq += packet.getDataLength() + 1;
            }
            ConsoleLog.fileLog(((float) sentBytes / file.length() * 100) + "%");
            window.addAll(packets);
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        send(window);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, timeout);
            this.send(packets);
            ackReceive();
        }
        packets.clear();
        packets.add(new TCPPacket(seq, null));
        this.send(packets);
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
        timer.cancel();
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
}
