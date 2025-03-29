package server.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReliableUdpSocket implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ReliableUdpSocket.class);
    private static final int BASE_RETRY_TIMEOUT_MS = 1000;
    private static final int WINDOW_SIZE = 5;
    private static final int MAX_SEQUENCE = Integer.MAX_VALUE;

    private final DatagramSocket socket;
    private volatile boolean isRunning = false;
    private ScheduledExecutorService scheduler;
    private final Lock controlLock = new ReentrantLock();
    private final Map<Integer, PacketInfo> pendingPackets = new ConcurrentHashMap<>();
    private final BlockingQueue<Message> receivedQueue = new LinkedBlockingQueue<>();
    private final TreeMap<Integer, Message> orderedBuffer = new TreeMap<>();
    private final AtomicInteger windowAvailable = new AtomicInteger(WINDOW_SIZE);

    private final Lock windowLock = new ReentrantLock();
    private final Condition windowNotFull = windowLock.newCondition();

    private int soTimeout = 0;
    private int packetSize = 65507;
    private final int payloadSize = packetSize - Packet.headerSize();
    private final AtomicInteger nextSeqNumber = new AtomicInteger(0);
    private final AtomicInteger lastAcked = new AtomicInteger(-1);
    private final AtomicInteger expectedSeqNumber = new AtomicInteger(0);


    public int getPayloadSize() {
        return payloadSize;
    }

    private static class PacketInfo {
        final byte[] data;
        final InetAddress address;
        final int port;
        int retries;
        long lastSentTime;

        PacketInfo(byte[] data, InetAddress address, int port) {
            this.data = data;
            this.address = address;
            this.port = port;
            this.retries = 0;
            this.lastSentTime = System.currentTimeMillis();
        }
    }

    private record Packet(boolean isAck, int sequenceNumber, byte[] data) {
        public static int headerSize() {
            return 2*Integer.BYTES + 1;
        }
    }

    public ReliableUdpSocket(int port, int packetSize) throws SocketException {
        this(port, packetSize, false);
    }

    public ReliableUdpSocket(int port, int packetSize, boolean toStart) throws SocketException {
        if (packetSize <= Packet.headerSize() || packetSize > 65507) {
            throw new IllegalArgumentException("Invalid packet size");
        }
        this.packetSize = packetSize;
        this.socket = new DatagramSocket(port);
        if (toStart) {
            startServices();
        }
    }

    public ReliableUdpSocket(int port, boolean toStart) throws SocketException {
        this.socket = new DatagramSocket(port);
        if (toStart) {
            startServices();
        }
    }

    public ReliableUdpSocket(boolean toStart) throws SocketException {
        this(65507, toStart);
    }

    public ReliableUdpSocket() throws SocketException {
        this(65507, false);
    }

    public ReliableUdpSocket(int port) throws SocketException {
        this(port, 65507);
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void startServices() {
        controlLock.lock();
        resetState();
        try {
            if (!isRunning) {
                isRunning = true;
                scheduler = Executors.newScheduledThreadPool(3);

                startReceiverThread();
                startRetryChecker();
                logger.info("Services started");
            }
        } finally {
            controlLock.unlock();
        }
    }

    private void startReceiverThread() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(() -> {
                byte[] buffer = new byte[packetSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (!scheduler.isShutdown()) {
                    try {
                        socket.receive(packet);
                        logger.info("received message, port {}", getPort());
                        processPacket(packet);
                    } catch (Exception e) {
                        if (!socket.isClosed()) {
                            logger.error("Receive error", e);
                        }
                    }
                }
            });
        }
    }

    private void processPacket(DatagramPacket udpPacket) throws IOException {
        Packet packet = deserialize(Arrays.copyOf(udpPacket.getData(), udpPacket.getLength()));
        logger.info("message {}, is ack: {}", new String(packet.data, StandardCharsets.UTF_8), packet.isAck());

        if (packet.isAck()) {
            handleAck(packet.sequenceNumber());
        } else {
            handleDataPacket(packet, udpPacket.getAddress(), udpPacket.getPort());
        }
    }

    private void handleDataPacket(Packet packet, InetAddress senderAddress, int senderPort) throws IOException {
        logger.trace("received packet: {}, expected packet: {}", packet.sequenceNumber, expectedSeqNumber);
        if(packet.sequenceNumber <= expectedSeqNumber.get()) {
            sendAck(packet.sequenceNumber(), senderAddress, senderPort);
        }
        bufferAndOrderPackets(packet, senderAddress, senderPort);
    }

    private synchronized void bufferAndOrderPackets(Packet packet, InetAddress address, int port) {
        int seq = packet.sequenceNumber();
        byte[] data = Arrays.copyOf(packet.data(), packet.data().length);

        orderedBuffer.put(seq, new Message(data, address, port, data.length));

        while (!orderedBuffer.isEmpty()) {
            int firstKey = orderedBuffer.firstKey();
            if (firstKey == expectedSeqNumber.get()) {
                Message msg = orderedBuffer.remove(firstKey);
                receivedQueue.add(msg);
                expectedSeqNumber.set(incrementSequence(firstKey));
            } else if (firstKey > expectedSeqNumber.get()) {
                break;
            } else {
                orderedBuffer.remove(firstKey);
            }
        }
    }

    private void startRetryChecker() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                pendingPackets.forEach((seq, info) -> {
                    long elapsed = now - info.lastSentTime;
                    int timeout = BASE_RETRY_TIMEOUT_MS * (1 << info.retries);

                    if (elapsed > timeout) {
                        resendPacket(seq, info);
                    }
                });
            }, 100, 100, TimeUnit.MILLISECONDS);
        }
    }

    private void resendPacket(int seqNumber, PacketInfo info) {
        try {
            DatagramPacket dp = new DatagramPacket(
                    info.data,
                    info.data.length,
                    info.address,
                    info.port
            );

            socket.send(dp);
            info.retries++;
            info.lastSentTime = System.currentTimeMillis();
            logger.trace("Resent packet [seq={}, retry={}]", seqNumber, info.retries);
        } catch (IOException e) {
            logger.error("Failed to resend packet [seq={}]: {}", seqNumber, e.getMessage());
        }
    }

    public void send(byte[] data, InetAddress address, int port) throws IOException {
        send(data, address, port, 0);
    }

    public void send(byte[] data, InetAddress address, int port, long timeoutMillis) throws IOException {
        final long startTime = System.currentTimeMillis();
        windowLock.lock();
        try {
            while (windowAvailable.get() <= 0) {
                if (timeoutMillis > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= timeoutMillis) {
                        throw new SocketTimeoutException("Send timeout after " + timeoutMillis + "ms");
                    }
                    long remaining = timeoutMillis - elapsed;
                    try {
                        windowNotFull.await(remaining, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Send interrupted", e);
                    }
                } else {
                    try {
                        windowNotFull.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Send interrupted", e);
                    }
                }
            }
            int currentSeq = nextSeqNumber.getAndIncrement();
            Packet packet = new Packet(false, currentSeq, data);
            byte[] bytes = serialize(packet);
            synchronized (pendingPackets) {
                pendingPackets.put(currentSeq, new PacketInfo(bytes, address, port));
                windowAvailable.decrementAndGet();
            }
            DatagramPacket dp = new DatagramPacket(bytes, bytes.length, address, port);
            socket.send(dp);
        } finally {
            windowLock.unlock();
        }
    }

    private void handleAck(int ackNumber) {
        logger.trace("received ACK {}", ackNumber);

        windowLock.lock();
        try {
            synchronized (pendingPackets) {
                    int delta = ackNumber - lastAcked.get();

                    // Обновляем счетчики
                    lastAcked.set(ackNumber);
                    pendingPackets.keySet().removeIf(seq -> seq <= ackNumber);

                    // Корректируем окно отправки
                    int newWindow = Math.min(WINDOW_SIZE, windowAvailable.get() + delta);
                    windowAvailable.set(newWindow);

            }
            windowNotFull.signalAll();
        } finally {
            windowLock.unlock();
        }
    }

    public Message receive(int timeout) throws SocketTimeoutException {
        try {
            Message msg = receivedQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (msg == null) throw new SocketTimeoutException("Receive timeout");
            return msg;
        } catch (InterruptedException e) {
            throw new SocketTimeoutException("Interrupted during receive");
        }
    }

    public Message receive() throws SocketTimeoutException {
        try {
            if (soTimeout > 0) return receive(soTimeout);
            return receivedQueue.take();
        } catch (InterruptedException e) {
            throw new SocketTimeoutException("Interrupted during receive");
        }
    }

    private void sendAck(int seqNumber, InetAddress senderAddress, int senderPort) throws IOException {
        Packet ack = new Packet(true, seqNumber, new byte[0]);
        byte[] bytes = serialize(ack);
        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, senderAddress, senderPort);
        socket.send(dp);
    }

    private byte[] serialize(Packet packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Packet.headerSize() + packet.data().length);
        buffer.putInt(packet.sequenceNumber());
        buffer.put(packet.isAck() ? (byte)1 : (byte)0);
        buffer.putInt(packet.data().length);
        buffer.put(packet.data());
        return buffer.array();
    }

    private Packet deserialize(byte[] rawData) throws IOException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(rawData);
            int sequenceNumber = buffer.getInt();
            boolean isAck = buffer.get() == 1;
            int dataLength = buffer.getInt();

            if (dataLength < 0 || dataLength > buffer.remaining()) {
                throw new IOException("Invalid packet length");
            }

            byte[] data = new byte[dataLength];
            buffer.get(data);
            return new Packet(isAck, sequenceNumber, data);
        } catch (BufferUnderflowException e) {
            throw new IOException("Malformed packet", e);
        }
    }

    private int incrementSequence(int seq) {
        return (seq == MAX_SEQUENCE) ? 0 : seq + 1;
    }

    public void setSoTimeout(int timeout) {
        this.soTimeout = Math.max(timeout, 0);
    }

    public void send(String message, InetAddress address, int port) throws IOException {
        send(message, address, port, 0);
    }

    public void send(String message, InetAddress address, int port, int timeout) throws IOException {
        send(message, StandardCharsets.UTF_8.name(), address, port, timeout);
    }

    public void send(String message, String charsetName, InetAddress address, int port, int timeout) throws IOException {
        try {
            byte[] data = message.getBytes(charsetName);
            send(data, address, port, timeout);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Unsupported charset: " + charsetName, e);
        }
    }

    public void send(String message, String charsetName, InetAddress address, int port) throws IOException {
        send(message, charsetName, address, port, 0);
    }

    public void stopServices() {
        controlLock.lock();
        resetState();
        try {
            if (isRunning) {
                isRunning = false;
                // Останавливаем пул потоков
                if (scheduler != null) {
                    scheduler.shutdown();
                    try {
                        if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        logger.error("Shutdown interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                    scheduler = null; // Важно: сбрасываем ссылку
                }
                logger.info("Services stopped");
            }
        } finally {
            controlLock.unlock();
        }
    }

    private void resetState() {
        nextSeqNumber.set(0);
        lastAcked.set(-1);
        expectedSeqNumber.set(0);
        pendingPackets.clear();
        receivedQueue.clear();
        orderedBuffer.clear();
        windowAvailable.set(WINDOW_SIZE);
    }

    @Override
    public void close() {
        stopServices();
        socket.close();
    }
}