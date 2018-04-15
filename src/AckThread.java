import java.io.IOException;
import java.net.DatagramPacket;
import java.util.ArrayList;

public class AckThread extends Thread {

    private TCPSocketImpl socketImpl;
    private EnhancedDatagramSocket UDPSocket;
    private boolean shouldRun = true;

    private ArrayList<Long> expectedAcks = new ArrayList<>();

    public AckThread(TCPSocketImpl socketImpl, EnhancedDatagramSocket UDPSocket) {
        this.socketImpl = socketImpl;
        this.UDPSocket = UDPSocket;
    }

    private int removeAcks(long ack) {
        int i;
        for (i = 0; i < expectedAcks.size(); i++) {
            if (ack < expectedAcks.get(i)) {
                break;
            }
        }
        for (int j = 0; j < i; j++)
            expectedAcks.remove(0);
        return i;
    }

    private void receiveAck() throws IOException {
        TCPPacket ack = null;
        while (ack == null || !(ack.getACK()
                /* && ack.getAcknowledgementNumber() >= this.expectedAcks.get(0)*/)) {
            byte[] ackData = new byte[this.UDPSocket.getPayloadLimitInBytes()];
            UDPSocket.receive(new DatagramPacket(ackData, ackData.length));
            ack = new TCPPacket(ackData);
        }
        int removedNo = removeAcks(ack.getAcknowledgementNumber());
        if (removedNo != 0)
            socketImpl.newAckReceived(removedNo);
        System.err.println("received ack : " + ack.getAcknowledgementNumber());
        socketImpl.setSeq(ack.getAcknowledgementNumber());
    }

    public void addExpectedAck(long newAck) {
        int i;
        for (i = 0; i < expectedAcks.size(); i++) {
            if (newAck < expectedAcks.get(i)) {
                break;
            }
        }
        expectedAcks.add(i, newAck);
    }

    public void stopRunning() {
        shouldRun = false;
    }

    @Override
    public void run() {
        super.run();
        while (shouldRun) {
            if (expectedAcks.size() > 0) {
                try {
                    receiveAck();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
