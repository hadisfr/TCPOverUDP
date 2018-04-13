import java.nio.ByteBuffer;

public class TCPPacket {
    private Long sequenceNumber;
    private Long acknowledgementNumber;
    private Boolean ACK;
    private Boolean SYN;
    private byte[] data;

    public static final int dataOffset = Long.SIZE + Long.SIZE +  Byte.SIZE +  Byte.SIZE;

    public int getSize() {
        return dataOffset + data.length * Byte.SIZE;
    }

    public int getBytesNumber() {
        return this.getSize() / Byte.SIZE + (this.getSize() % Byte.SIZE != 0 ? 1 : 0);
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
        this.data = data != null ? data : new byte[0];
    }

    public TCPPacket(Long sequenceNumber, byte[] data) {
        this(sequenceNumber, (long)(0), false, false, data);
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
        ByteBuffer res = ByteBuffer.allocate(this.getBytesNumber());
        res.putLong(this.sequenceNumber);
        res.putLong(this.acknowledgementNumber);
        res.put((byte) (ACK ? 1 : 0));
        res.put((byte) (SYN ? 1 : 0));
        res.put(this.data);
        return res.array();
    }
}
