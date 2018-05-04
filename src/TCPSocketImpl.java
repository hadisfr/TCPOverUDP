import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

public class TCPSocketImpl extends TCPSocket {
    private EnhancedDatagramSocket UDPSocket;
    private InetAddress serverIp;
    private int serverPort;

    private int SSThreshold;
    private static final int DefaultSSThreshold = 100;
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
        this.SSThreshold = DefaultSSThreshold;
        this.windowSize = this.getMSS();
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

    public void handleLoss() throws Exception {
        ConsoleLog.windowLog("-> SS (LOSS)");
        this.state = State.SLOW_START;
        this.SSThreshold = (int) (this.windowSize / 2);
        this.windowSize = 1;
        this.onWindowChange();
        this.lastSentIndex = -1;
        this.send();
    }

    private void resetTimer() {
        if (timer != null)
            timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    ConsoleLog.windowLog("TIME_OUT");
                    handleLoss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, timeout);
    }

    private void windowPop() {
        resetTimer();
        window.remove(0);
        this.lastSentIndex--;
        if (timer != null && this.window.size() == 0)
            timer.cancel();
    }

    private void windowPush(TCPPacket packet) {
        if (this.window.size() == 0)
            resetTimer();
        this.window.add(packet);
    }

    private void windowPush(Collection<? extends TCPPacket> packets) {
        if (this.window.size() == 0)
            resetTimer();
        this.window.addAll(packets);
    }

    private void send(boolean isResend) throws Exception {
        for (int i = isResend ? 0 : (this.lastSentIndex + 1); i < windowSize && i < window.size(); i++) {
            ConsoleLog.windowLog(String.format(
                    "windowSize: %f,\twindow.size: %d,\tlastSentIndex:%d%s,\tindex: %d,\tSST: %d,\tstatus: %s",
                    this.windowSize,
                    this.window.size(),
                    this.lastSentIndex,
                    isResend ? " (RESEND)" : "",
                    i,
                    this.SSThreshold,
                    this.state == State.CONGESTION_AVOIDANCE ? "CA" : this.state == State.SLOW_START ? "SS" : "UNK"
            ));
            TCPPacket packet = window.get(i);
            this.UDPSocket.send(new DatagramPacket(packet.toUDPData(), packet.getBytesNumber(),
                    this.serverIp, this.serverPort));
            ConsoleLog.fileLog("This sent packet is: " + packet.getSequenceNumber());
            if (!isResend)
                this.lastSentIndex = i;
        }
    }

    private void send() throws Exception {
        send(false);
    }

    private void ackReceive() throws Exception {
        TCPPacket ack;
        while (true) {
            ack = null;
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
                duplicateAcks++;
                if (duplicateAcks == 3) {
                    this.handleLoss();
//                    this.duplicateAcks = 0;
                }
                continue;
            }
            this.duplicateAcks = 0;
            for (int j = 0; j < firstUnackedPacketIndex; j++) {
                this.windowPop();
                switch (this.state) {
                    case CONGESTION_AVOIDANCE:
                        this.windowSize += this.getMSS() * this.getMSS() / this.windowSize;
                        break;
                    case SLOW_START:
                        this.windowSize += this.getMSS();
                        if (this.windowSize > this.SSThreshold) {
                            ConsoleLog.windowLog(String.format("-> CA,\twindowSize: %f,\tSST: %d",
                                    this.windowSize, this.SSThreshold));
                            this.state = State.CONGESTION_AVOIDANCE;
                        }
                        break;
                }
                this.onWindowChange();
            }
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
            packets.clear();
            for (int i = 0; windowSize > lastSentIndex + i + 1 && readBytes < file.length(); i++) {
                byte[] data = new byte[this.UDPSocket.getPayloadLimitInBytes() - TCPPacket.dataOffsetByBytes];
                readBytes += buffer.read(data, 0, data.length);
                TCPPacket packet = new TCPPacket(seq, data);
                packets.add(packet);
                seq += packet.getDataLength() + 1;
            }
            this.windowPush(packets);
            this.send();
            ConsoleLog.fileLog(String.format("%f%%,\twindowSize: %f,\twindow.size: %d,\tlastSentIndex:%d",
                    ((float) readBytes / file.length() * 100),
                    this.windowSize, this.window.size(), this.lastSentIndex));
            ackReceive();
        }
        buffer.close();
        TCPPacket finPacket = new TCPPacket(seq, null);
        seq += finPacket.getDataLength() + 1;
        this.windowPush(finPacket);
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
