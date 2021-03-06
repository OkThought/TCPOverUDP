package ru.nsu.ccfit.bogush.tou;

import ru.nsu.ccfit.bogush.tcp.TCPSegment;
import ru.nsu.ccfit.bogush.tcp.TCPSegmentType;
import ru.nsu.ccfit.bogush.tcp.TCPUnknownSegmentTypeException;

import java.net.InetAddress;

public class TOUSystemMessage extends TOUSegment {
    private static final TCPSegmentType DEFAULT_TYPE = TCPSegmentType.ACK;

    private TCPSegmentType type;

    TOUSystemMessage() {
        this(DEFAULT_TYPE);
    }

    TOUSystemMessage(TCPSegmentType type) {
        this(type,
            null, 0,
            null, 0,
            0, 0);
    }

    TOUSystemMessage(TOUSystemMessage that) {
        this(that, that.type);
    }

    TOUSystemMessage(TOUSegment segment)
            throws TCPUnknownSegmentTypeException {
        this(segment, segment.type());
    }

    TOUSystemMessage(TOUSegment segment, TCPSegmentType type) {
        super(new TCPSegment(segment.tcpSegment.header()), segment.sourceAddress, segment.destinationAddress);
        this.type = type;
    }

    TOUSystemMessage(TCPSegmentType type,
                     InetAddress sourceAddress, int sourcePort,
                     InetAddress destinationAddress, int destinationPort,
                     int systemMessage, long timeout) {
        this(type, sourceAddress, sourcePort, destinationAddress, destinationPort,
                sequencePart(systemMessage), ackPart(systemMessage), timeout);
    }

    TOUSystemMessage(TCPSegmentType type,
                     InetAddress sourceAddress, int sourcePort,
                     InetAddress destinationAddress, int destinationPort,
                     short sequenceNumber, short ackNumber,
                     long timeout) {
        super(new TCPSegment(), sourceAddress, destinationAddress, timeout);
        this.type = type;
        super.type(type);
        super.sourcePort(sourcePort);
        super.destinationPort(destinationPort);
        super.sequenceNumber(sequenceNumber);
        super.ackNumber(ackNumber);
    }

    private static short sequencePart(int systemMessage) {
        return (short) (systemMessage >> 16);
    }

    private static short ackPart(int systemMessage) {
        return (short) systemMessage;
    }

    boolean isEqualTo(TOUSystemMessage that) {
        if (this == that) return true;
        if (that == null) return false;
        if (type != that.type) return false;
        if (sourcePort() != 0 && that.sourcePort() != 0 && sourcePort() != that.sourcePort()) return false;
        if (destinationPort() != 0 && that.destinationPort() != 0 &&
                destinationPort() != that.destinationPort()) return false;
        boolean seqEqual = type != TCPSegmentType.ACK || sequenceNumber() == that.sequenceNumber();
        if (!seqEqual) return false;
        boolean ackEqual = !tcpSegment.isACK() || ackNumber() == that.ackNumber();
        if (!ackEqual) return false;
        if (sourceAddress != null && that.sourceAddress != null && !sourceAddress.equals(that.sourceAddress)) return false;
        return destinationAddress == null || that.destinationAddress == null || destinationAddress.equals(that.destinationAddress);
    }

    @Override
    public void type(TCPSegmentType type) {
        this.type = type;
        super.type(type);
    }

    @Override
    public TCPSegmentType type() {
        return type;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (sourceAddress != null ? sourceAddress.hashCode() : 0);
        result = 31 * result + sourcePort();
        result = 31 * result + (destinationAddress != null ? destinationAddress.hashCode() : 0);
        result = 31 * result + destinationPort();
        result = 31 * result + (int) sequenceNumber();
        result = 31 * result + (int) ackNumber();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%16s[%s seq: %5d ack: %5d from %s:%5d to %s:%5d data offset: %3d size: %3d bytes]",
                type, tcpSegment.typeByteToString(), sequenceNumber() & 0xffff, ackNumber() & 0xffff,
                sourceAddress, sourcePort(), destinationAddress, destinationPort(),
                tcpSegment.dataOffset(), tcpSegment.size());
    }
}
