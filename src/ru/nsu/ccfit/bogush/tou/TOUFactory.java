package ru.nsu.ccfit.bogush.tou;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.ccfit.bogush.tcp.TCPSegment;
import ru.nsu.ccfit.bogush.tcp.TCPSegmentType;
import ru.nsu.ccfit.bogush.tcp.TCPUnknownSegmentTypeException;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;

import static ru.nsu.ccfit.bogush.tcp.TCPSegmentType.*;

class TOUFactory {
    static { TOULog4JUtils.initIfNotInitYet(); }
    private static final Logger LOGGER = LogManager.getLogger("SegmentFactory");

    private final TOUSocketImpl impl;

    TOUFactory(TOUSocketImpl impl) {
        this.impl = impl;
    }

    static DatagramPacket packIntoUDP(TOUSegment segment) {
        return packIntoUDP(segment.tcpSegment(), segment.destinationAddress());
    }

    static DatagramPacket packIntoUDP(TCPSegment segment, InetAddress destinationAddress) {
        byte[] data = segment.bytes();
        return new DatagramPacket(data, data.length, destinationAddress, segment.destinationPort());
    }

    static TCPSegment unpackTCP(DatagramPacket segment) {
        TCPSegment p = new TCPSegment(segment.getData(), segment.getOffset(), segment.getLength());
        p.sourcePort(segment.getPort());
        return p;
    }

    static DatagramPacket packIntoUDP(TOUSystemMessage systemMessage) {
        TCPSegment tcpSegment = createTCPSegment(systemMessage);
        return packIntoUDP(tcpSegment, systemMessage.destinationAddress());
    }

    static TCPSegment createTCPSegment(TOUSystemMessage systemMessage) {
        TCPSegment p = new TCPSegment();
        p.flags(systemMessage.type().toByte());
        p.ackNumber(systemMessage.ackNumber());
        p.sequenceNumber(systemMessage.sequenceNumber());
        p.sourcePort(systemMessage.sourcePort());
        p.destinationPort(systemMessage.destinationPort());
        return p;
    }

    static TOUSegment createTOUSegmentByAck(TOUSystemMessage systemMessage) {
        TCPSegment p = new TCPSegment();
        p.sourcePort(systemMessage.destinationPort());
        p.destinationPort(systemMessage.sourcePort());
        p.sequenceNumber(systemMessage.ackNumber());
        return new TOUSegment(p, systemMessage.destinationAddress(), systemMessage.sourceAddress());
    }

    static TOUSystemMessage createSYNorFIN(TCPSegmentType type, InetAddress srcAddr, int srcPort, InetAddress dstAddr, int dstPort) {
        LOGGER.traceEntry("source: {}:{} destination: {}:{}", srcAddr, srcPort, dstAddr, dstPort);
        return LOGGER.traceExit(new TOUSystemMessage(type, srcAddr, srcPort, dstAddr, dstPort, rand(), (short) 0));
    }

    private static short rand() {
        LOGGER.traceEntry();
        return LOGGER.traceExit((short) ThreadLocalRandom.current().nextInt());
    }

    static TOUSystemMessage createSYNACKorFINACK(InetAddress localAddress, int localPort, TOUSystemMessage synOrFin) {
        LOGGER.traceEntry("local address: {}:{} SYN: {}", localAddress, localPort, synOrFin);

        TOUSystemMessage synack = new TOUSystemMessage(synOrFin);
        synack.sourceAddress(localAddress);
        synack.sourcePort(localPort);
        synack.destinationAddress(synOrFin.sourceAddress());
        synack.destinationPort(synOrFin.sourcePort());
        synack.type(synOrFin.type() == SYN ? SYNACK : FINACK);
        synack.ackNumber((short) (synOrFin.sequenceNumber() + 1));
        synack.sequenceNumber(rand());

        return LOGGER.traceExit(synack);
    }

    static TOUSystemMessage createACK(TOUSystemMessage synackOrFinack) {
        LOGGER.traceEntry(() -> synackOrFinack);

        TCPSegmentType type = synackOrFinack.type();
        TOUSystemMessage ack = new TOUSystemMessage(synackOrFinack);
        ack.sourceAddress(synackOrFinack.destinationAddress());
        ack.sourcePort(synackOrFinack.destinationPort());
        ack.destinationAddress(synackOrFinack.sourceAddress());
        ack.destinationPort(synackOrFinack.sourcePort());
        ack.sequenceNumber(synackOrFinack.ackNumber());
        ack.ackNumber((short) (synackOrFinack.sequenceNumber() + 1));
        ack.type(ACK);

        return LOGGER.traceExit(ack);
    }

    static TOUSystemMessage createSegmentACK(short sequenceNumber, InetSocketAddress local, InetSocketAddress remote) {
        LOGGER.traceEntry("seq: {} local: {} remote: {}", sequenceNumber, local, remote);

        TOUSystemMessage ack = new TOUSystemMessage();
        ack.destinationAddress(remote.getAddress());
        ack.destinationPort(remote.getPort());
        ack.sourceAddress(local.getAddress());
        ack.sourcePort(local.getPort());
        ack.ackNumber(sequenceNumber);
        ack.type(ACK);

        return LOGGER.traceExit(ack);
    }

    static boolean canMerge(TOUSegment dataSegment, TOUSystemMessage systemMessage) {
        if (dataSegment.typeByte() != 0) return false;
        if (dataSegment.destinationPort() != systemMessage.destinationPort()) return false;
        if (!dataSegment.destinationAddress().equals(systemMessage.destinationAddress())) return false;
        switch (systemMessage.type()) {
            case ACK:
                return true;
            case SYN:
            case FIN:
                return dataSegment.sequenceNumber() == systemMessage.systemMessage();
            case SYNACK:
            case FINACK:
                return dataSegment.sequenceNumber() == systemMessage.sequenceNumber();
        }
        return false;
    }

    static boolean isMergedWithSystemMessage(TOUSegment dataSegment, TOUSystemMessage systemMessage) throws TCPUnknownSegmentTypeException {
        switch (dataSegment.type()) {
            case ACK:
                return dataSegment.ackNumber() == systemMessage.systemMessage();
            case SYN:
            case FIN:
                return dataSegment.sequenceNumber() == systemMessage.systemMessage();
            case SYNACK:
            case FINACK:
                return dataSegment.sequenceAndAckNumbers() == systemMessage.systemMessage();
        }
        return false;
    }

    static void merge(TOUSegment dataSegment, TOUSystemMessage systemMessage) {
        dataSegment.type(systemMessage.type());
        dataSegment.ackNumber(systemMessage.ackNumber());
    }

    static void unmerge(TOUSegment dataSegment) throws TCPUnknownSegmentTypeException {
        dataSegment.type(ORDINARY);
        dataSegment.ackNumber((short) 0);
    }

    static TOUSystemMessage generateDataSegmentKey(TCPSegment segment, InetAddress srcAddr, InetAddress dstAddr) {
        TOUSystemMessage key = new TOUSystemMessage();
        key.sourceAddress(srcAddr);
        key.sourcePort(segment.sourcePort());
        key.destinationAddress(dstAddr);
        key.destinationPort(segment.destinationPort());
        key.sequenceNumber(segment.sequenceNumber());
        return key;
    }

    static TOUSystemMessage generateSystemMessageKey(TCPSegment tcpSegment, InetAddress srcAddr, InetAddress dstAddr)
            throws TCPUnknownSegmentTypeException {
        return generateSystemMessageKey(TCPSegmentType.typeOf(tcpSegment),
                srcAddr, tcpSegment.sourcePort(),
                dstAddr, tcpSegment.destinationPort(),
                tcpSegment.sequenceNumber(), tcpSegment.ackNumber());
    }

    static TOUSystemMessage generateSystemMessageKey(TCPSegment tcpSegment, TCPSegmentType type,
                                                    InetAddress srcAddr, InetAddress dstAddr) {
        return generateSystemMessageKey(type,
                srcAddr, tcpSegment.sourcePort(),
                dstAddr, tcpSegment.destinationPort(),
                tcpSegment.sequenceNumber(), tcpSegment.ackNumber());
    }

    static TOUSystemMessage generateSystemMessageKey(TOUSystemMessage systemMessage) {
        return generateSystemMessageKey(systemMessage.type(),
                systemMessage.sourceAddress(), systemMessage.sourcePort(),
                systemMessage.destinationAddress(), systemMessage.destinationPort(),
                systemMessage.sequenceNumber(), systemMessage.ackNumber());
    }

    static TOUSystemMessage generateSystemMessageKey(TCPSegmentType type,
                                                    InetAddress srcAddr, int srcPort,
                                                    InetAddress dstAddr, int dstPort,
                                                    short seq, short ack) {
        TOUSystemMessage key = new TOUSystemMessage(type);
        key.destinationAddress(dstAddr);
        key.destinationPort(dstPort);
        switch (type) {
            case ACK:
                key.sourceAddress(srcAddr);
                key.sourcePort(srcPort);
                key.sequenceNumber(seq);
                key.ackNumber(ack);
                break;
            case SYN:
                break;
            case SYNACK:
                key.sourceAddress(srcAddr);
                key.sourcePort(srcPort);
                key.ackNumber(ack);
                break;
            case FIN:
                break;
            case FINACK:
                key.sourceAddress(srcAddr);
                key.sourcePort(srcPort);
                key.ackNumber(ack);
                break;
        }
        return key;
    }

    static TOUSystemMessage createSystemMessage(TCPSegment tcpSegment, InetAddress srcAddr, InetAddress dstAddr)
            throws TCPUnknownSegmentTypeException {
        return createSystemMessage(tcpSegment, TCPSegmentType.typeOf(tcpSegment), srcAddr, dstAddr);
    }

    static TOUSystemMessage createSystemMessage(TCPSegment tcpSegment, TCPSegmentType type, InetAddress srcAddr, InetAddress dstAddr) {
        TOUSystemMessage systemMessage = new TOUSystemMessage(type);
        systemMessage.sourceAddress(srcAddr);
        systemMessage.sourcePort(tcpSegment.sourcePort());
        systemMessage.destinationAddress(dstAddr);
        systemMessage.destinationPort(tcpSegment.destinationPort());
        systemMessage.sequenceNumber(tcpSegment.sequenceNumber());
        systemMessage.ackNumber(tcpSegment.ackNumber());
        return systemMessage;
    }

    TOUSystemMessage createSYNACKorFINACK(TOUSystemMessage synOrFin) {
        return createSYNACKorFINACK(impl.localAddress(), impl.localPort(), synOrFin);
    }

    TOUSystemMessage createSegmentACK(short sequenceNumber) {
        return createSegmentACK(sequenceNumber, impl.localSocketAddress(), impl.remoteSocketAddress());
    }
}