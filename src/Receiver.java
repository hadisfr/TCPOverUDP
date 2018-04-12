import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class Receiver {
    public static void main(String[] args) throws Exception {
        TCPServerSocket tcpServerSocket = new TCPServerSocketImpl(12345);
        TCPSocket tcpSocket = tcpServerSocket.accept();
        tcpSocket.receiveAndLog("receiving.mp3");
        tcpSocket.close();
        tcpServerSocket.close();
    }
}
