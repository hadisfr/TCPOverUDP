import java.nio.ByteBuffer;

public class TCPPacket {
    private Long sequenceNumber;
    private Long acknowledgementNumber;
    private Boolean ACK;
    private Boolean SYN;
    private int dataLength;
    private byte[] data;

    public static final int dataOffset = Long.SIZE + Long.SIZE + Byte.SIZE + Byte.SIZE + Integer.SIZE;
    public static final int dataOffsetByBytes = dataOffset / Byte.SIZE + (dataOffset % Byte.SIZE != 0 ? 1 : 0);

    public int getSize() {
        return dataOffset + this.getDataLength() * Byte.SIZE;
    }

    public int getBytesNumber() {
        return dataOffsetByBytes + this.getDataLength();
    }

    public TCPPacket(byte[] UDPData) {
        ByteBuffer buffer = ByteBuffer.allocate(UDPData.length);
        buffer.put(UDPData);
        buffer.flip();
        this.sequenceNumber = buffer.getLong();
        this.acknowledgementNumber = buffer.getLong();
        this.ACK = buffer.get() != 0;
        this.SYN = buffer.get() != 0;
        this.dataLength = buffer.getInt();
        this.data = new byte[this.dataLength];
        buffer.get(this.data, 0, this.dataLength);
    }

    public TCPPacket(Long sequenceNumber, Long acknowledgementNumber, Boolean ACK, Boolean SYN, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.acknowledgementNumber = acknowledgementNumber;
        this.ACK = ACK;
        this.SYN = SYN;
        this.data = data != null ? data : new byte[1];
        this.dataLength = this.data.length;
    }

    public TCPPacket(Long sequenceNumber, byte[] data) {
        this(sequenceNumber, (long) (0), false, false, data);
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

    public int getDataLength() {
        return dataLength;
    }

    public byte[] toUDPData() {
        ByteBuffer res = ByteBuffer.allocate(this.getBytesNumber());
        res.putLong(this.sequenceNumber);
        res.putLong(this.acknowledgementNumber);
        res.put((byte) (ACK ? 1 : 0));
        res.put((byte) (SYN ? 1 : 0));
        res.putInt(this.dataLength);
        res.put(this.data);
        return res.array();
    }
}
