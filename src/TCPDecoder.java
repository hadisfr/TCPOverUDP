import java.nio.ByteBuffer;
import java.util.*;

public class TCPDecoder {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String str = scanner.next();
        byte[] data = new byte[str.length() / 2];
        for (int i = 0; i < str.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + (byte) Character.digit(str.charAt(i + 1), 16));
        }
        TCPPacket packet = new TCPPacket(data);
        System.out.println("sequenceNumber:\t\t" + packet.getSequenceNumber());
        System.out.println("acknowledgementNumber:\t" + packet.getAcknowledgementNumber());
        System.out.println("ACK:\t\t\t" + packet.getACK());
        System.out.println("SYN:\t\t\t" + packet.getSYN());
        System.out.println("data (as Hex):\t\t" + Integer.toHexString(toInt(packet.getData())));
        System.out.println("data (as String):\t" + new String(packet.getData()));
    }

    private static int toInt(byte[] bytes) {
        int ret = 0;
        for (byte b : bytes) {
            ret <<= 8;
            ret |= (int) b & 0xFF;
        }
        return ret;
    }
}
