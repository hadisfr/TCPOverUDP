import java.nio.ByteBuffer;

public class TCPPacket {
    private Long sequenceNumber;
    private Long acknowledgementNumber;
    private Boolean ACK;
    private Boolean SYN;
    private byte[] data;

    private static final int dataOffset = Long.SIZE + Long.SIZE +  Byte.SIZE +  Byte.SIZE;

    public int SIZE() {
        return dataOffset + data.length * Byte.SIZE;
    }

    public int BYTES() {
        return this.SIZE() / Byte.SIZE + (this.SIZE() % Byte.SIZE != 0 ? 1 : 0);
    }

    public TCPPacket(byte[] UDPData) {
        ByteBuffer buffer = ByteBuffer.allocate(UDPData.length);
        buffer.put(UDPData);
        buffer.flip();
        this.sequenceNumber = buffer.getLong();
        this.acknowledgementNumber = buffer.getLong();
        this.ACK = buffer.get() != 0;
        this.SYN = buffer.get() != 0;
        this.data = new byte[buffer.remaining()];
        buffer.get(this.data, 0, buffer.remaining());
    }

    public TCPPacket(Long sequenceNumber, Long acknowledgementNumber, Boolean ACK, Boolean SYN, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.acknowledgementNumber = acknowledgementNumber;
        this.ACK = ACK;
        this.SYN = SYN;
        this.data = data;
    }

    public TCPPacket(Long sequenceNumber, Long acknowledgementNumber, byte[] data) {
        this(sequenceNumber, acknowledgementNumber, false, false, data);
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public Long getAcknowledgementNumber() {
        return acknowledgementNumber;
    }

    public Boolean getACK() {
        return ACK;
    }

    public Boolean getSYN() {
        return SYN;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] toUDPData() {
        ByteBuffer res = ByteBuffer.allocate(this.BYTES());
        res.putLong(this.sequenceNumber);
        res.putLong(this.acknowledgementNumber);
        res.put((byte) (ACK ? 1 : 0));
        res.put((byte) (SYN ? 1 : 0));
        res.put(this.data);
        return res.array();
    }
}
