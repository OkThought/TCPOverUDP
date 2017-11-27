package ru.nsu.ccfit.bogush.tou;

import ru.nsu.ccfit.bogush.tcp.TCPUnknownPacketTypeException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class TOUSender extends Thread {
    private final DatagramSocket udpSocket;
    private final BlockingQueue<TOUPacket> dataPackets;
    private final BlockingQueue<TOUSystemPacket> systemPackets;

    TOUSender(DatagramSocket udpSocket, int queueCapacity) throws IOException {
        this.udpSocket = udpSocket;
        dataPackets = new ArrayBlockingQueue<>(queueCapacity);
        systemPackets = new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                while (!systemPackets.isEmpty()) {
                    TOUPacket dataPacket = tryToMergeWithAnyDataPacket(systemPackets.peek());
                    if (dataPacket != null) {
                        send(dataPacket);
                    } else {
                        sendSystemPacket();
                    }
                }
                sendDataPacket();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private void sendDataPacket() throws IOException, InterruptedException {
        TOUPacket dataPacket;
        synchronized (dataPackets) {
            dataPacket = dataPackets.take();
            dataPackets.put(dataPacket);
        }
        send(dataPacket);
    }

    private void sendSystemPacket() throws InterruptedException, IOException {
        TOUSystemPacket systemPacket;
        synchronized (systemPackets) {
            systemPacket = systemPackets.take();
            systemPackets.put(systemPacket);
        }
        send(systemPacket);
    }

    private void send(TOUSystemPacket systemPacket) throws IOException {
        DatagramPacket udpPacket = TOUPacketFactory.encapsulateIntoUDP(systemPacket);
        udpSocket.send(udpPacket);
    }

    private void send(TOUPacket dataPacket) throws IOException {
        DatagramPacket udpPacket = TOUPacketFactory.encapsulateIntoUDP(dataPacket);
        udpSocket.send(udpPacket);
    }

    private TOUPacket tryToMergeWithAnyDataPacket(TOUSystemPacket systemPacket) throws InterruptedException {
        for (TOUPacket dataPacket: dataPackets) {
            if (TOUPacketFactory.canMerge(dataPacket, systemPacket)) {
                TOUPacketFactory.mergeSystemPacket(dataPacket, systemPacket);
                synchronized (dataPackets) {
                    dataPackets.remove(dataPacket);
                    dataPackets.put(dataPacket);
                }
                return dataPacket;
            }
        }
        return null;
    }

    void sendOnce(TOUPacket packet) throws IOException {
        udpSocket.send(TOUPacketFactory.encapsulateIntoUDP(packet));
    }

    void sendOnce(TOUSystemPacket systemPacket) throws IOException {
        udpSocket.send(TOUPacketFactory.encapsulateIntoUDP(systemPacket));
    }

    void putInQueue(TOUPacket packet) throws InterruptedException {
        dataPackets.put(packet);
    }

    void putInQueue(TOUSystemPacket systemPacket) throws InterruptedException {
        systemPackets.put(systemPacket);
    }

    void removeDataPacket(short sequenceNumber) {
        dataPackets.removeIf(packet -> packet.sequenceNumber() == sequenceNumber);
    }

    boolean removeFromQueue(TOUSystemPacket systemPacket) throws TCPUnknownPacketTypeException {
        boolean removed = systemPackets.remove(systemPacket);
        if (removed) return true;
        for (TOUPacket dataPacket: dataPackets) {
            if (TOUPacketFactory.isMergedWithSystemPacket(dataPacket, systemPacket)) {
                TOUPacketFactory.unmergeSystemPacket(dataPacket);
                return true;
            }
        }
        return false;
    }

    void removeFromQueue(TOUPacket packet) {
        dataPackets.remove(packet);
    }
}
