import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class TCPSocketImpl extends TCPSocket {
    private EnhancedDatagramSocket UDPSocket;
    private InetAddress serverIp;
    private int serverPort;

    private int SSThreshold;
    private long seq = 100;
    private long expectedSeq;
    private final int timeout = 2000;
    private int duplicateAcks;
    private float windowSize;
    private int lastSentIndex;
    private ArrayList<TCPPacket> window = new ArrayList<>();

    private Timer timer;

    public enum State {
        NONE,  // client
        SYN_SENT,  // client
        SYN_RECEIVED,  // server
        ESTABLISHED,  // client
        SLOW_START,  // server
        CONGESTION_AVOIDANCE  // server
    }

    private State state;

    private TCPSocketImpl(String ip, int port, State state) throws Exception {
        super(ip, port);
        this.UDPSocket = new EnhancedDatagramSocket(0);
        this.serverIp = InetAddress.getByName(ip);
        this.serverPort = port;
        this.state = state;
        this.lastSentIndex = -1;
        this.SSThreshold = 0;
        this.windowSize = 1;
        this.duplicateAcks = 0;
        this.onWindowChange();
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
        this.state = State.SLOW_START;
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

    public int getMSS() {
//        return this.UDPSocket.getPayloadLimitInBytes() + TCPPacket.dataOffsetByBytes;
        return 1;
    }

    public void handleLoss() {
        this.state = State.SLOW_START;
        this.SSThreshold = (int) (this.windowSize / 2);
        this.windowSize = 1;
        this.onWindowChange();
    }

    private void send(boolean isResend) throws Exception {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    send(true);
                    handleLoss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, timeout);
        for (int i = isResend ? 0 : (this.lastSentIndex + 1); i < windowSize && i < window.size(); i++) {
            ConsoleLog.windowLog(String.format("windowSize: %f,\twindow.size: %d,\tlastSentIndex:%d%s,\tindex: %d",
                    this.windowSize, this.window.size(), this.lastSentIndex, isResend ? " (RESEND)" : "", i));
            TCPPacket packet = window.get(i);
            this.UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                    this.serverIp, this.serverPort));
            ConsoleLog.fileLog("This sent packet is : " + packet.getSequenceNumber());
            if (!isResend)
                this.lastSentIndex = i;
        }
    }

    private void send() throws Exception {
        send(false);
    }

    private void ackReceive() throws IOException {
        TCPPacket ack = null;
        while (true) {
            while (ack == null || !(ack.getACK())) {
                byte[] ackData = new byte[this.UDPSocket.getPayloadLimitInBytes()];
                UDPSocket.receive(new DatagramPacket(ackData, ackData.length));
                ack = new TCPPacket(ackData);
            }
            int firstUnackedPacketIndex;
            for (firstUnackedPacketIndex = 0; firstUnackedPacketIndex < window.size(); firstUnackedPacketIndex++)
                if (ack.getAcknowledgementNumber()
                        < window.get(firstUnackedPacketIndex).getExpectedAcknowledgementNumber())
                    break;
            if (firstUnackedPacketIndex == 0) {
                if (this.state == State.CONGESTION_AVOIDANCE) {
                    duplicateAcks++;
                    if (duplicateAcks >= 3) {
                        this.handleLoss();
                        this.duplicateAcks = 0;
                    }
                }
                continue;
            }
            timer.cancel();
            for (int j = 0; j < firstUnackedPacketIndex; j++) {
                window.remove(0);
                this.lastSentIndex--;
                switch (this.state) {
                    case CONGESTION_AVOIDANCE:
//                        ConsoleLog.fileLog("Before: " + this.windowSize);
                        this.windowSize += this.getMSS() * this.getMSS() / this.windowSize;
//                        ConsoleLog.fileLog("After: " + this.windowSize);
                        break;
                    case SLOW_START:
//                        ConsoleLog.fileLog("Before: " + this.windowSize);
                        this.windowSize += this.getMSS();
//                        ConsoleLog.fileLog("After: " + this.windowSize);
                        if (this.windowSize > this.SSThreshold)
                            this.state = State.CONGESTION_AVOIDANCE;
                        break;
                }
                this.onWindowChange();
            }
            ConsoleLog.fileLog("Window size: " + this.windowSize);
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

    private void printWindow() {
        String result = "";
        for (TCPPacket p : window) {
            result += p.getSequenceNumber() + ", ";
        }
        ConsoleLog.fileLog("Window : " + result);
    }

    @Override
    public void send(String pathToFile) throws Exception {
        File file = new File(pathToFile);
        BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(file));
        ArrayList<TCPPacket> packets = new ArrayList<TCPPacket>();
        ConsoleLog.fileLog("Start sending " + pathToFile);
        int readBytes = 0;
        while (readBytes < file.length()) {
            ConsoleLog.fileLog(String.format("windowSize: %f,\twindow.size: %d,\tlastSentIndex:%d",
                    this.windowSize, this.window.size(), this.lastSentIndex));
            packets.clear();
            for (int i = 0; windowSize > lastSentIndex + i + 1 && readBytes < file.length(); i++) {
                byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes() - TCPPacket.dataOffsetByBytes];
                readBytes += buffer.read(data, 0, data.length);
                TCPPacket packet = new TCPPacket(seq, data);
                packets.add(packet);
//                ConsoleLog.fileLog("new packet : " + packet.getSequenceNumber());
                seq += packet.getDataLength() + 1;
            }
//            ConsoleLog.fileLog("pack size " + packets.size());
            window.addAll(packets);
//            printWindow();
            this.send();
            ConsoleLog.fileLog(String.format("windowSize: %f,\twindow.size: %d,\tlastSentIndex:%d",
                    this.windowSize, this.window.size(), this.lastSentIndex));
            ConsoleLog.fileLog(((float) readBytes / file.length() * 100) + "%");
            ackReceive();
        }
        buffer.close();
        TCPPacket finPacket = new TCPPacket(seq, null);
        seq += finPacket.getDataLength() + 1;
        window.add(finPacket);
        while (window.size() > 0) {
            this.send();
            ackReceive();
        }
        ConsoleLog.fileLog("End sending " + pathToFile);
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        ConsoleLog.fileLog("Start receiving " + pathToFile);
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
        ConsoleLog.fileLog("End receiving " + pathToFile);
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
        return (long) windowSize;
    }
}
