package server.downloader;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.socket.Message;
import server.socket.ReliableUdpSocket;
import server.status.Status;

import java.io.*;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class UdpDownloader implements Downloader {
    private static final Logger logger = LoggerFactory.getLogger(UdpDownloader.class);
    private DownloadRequest lastDownloadRequest = null;
    private UploadRequest lastUploadRequest = null;
    private final ReliableUdpSocket socket;
    private final int bufferSize;
    private final int port;
    private final int sendTimeout;

    public UdpDownloader(ReliableUdpSocket socket, int port, int bufferSize, int sendTimeout) {
        this.socket = socket;
        this.port = port;
        this.bufferSize = bufferSize;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void downloadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException {
        var currentRequest = new DownloadRequest(clientAddress, fileName);
        try (var input = new FileInputStream(fileName)) {
            if (!cont || !currentRequest.equals(lastDownloadRequest) || lastDownloadRequest.isSuccessful()) {
                if (cont) {
                    socket.send("Невозможно продолжить, нет подходящих данных сессии", clientAddress, port);
                    return;
                }
                lastDownloadRequest = currentRequest;
                logger.debug("{} Accept", Status.SUCCESS);
                accept(clientAddress);
            } else {
                accept(clientAddress);
                currentRequest.addProgress(readLong());
                logger.debug("Continue");
                long skipped = input.skip(currentRequest.getProgress());
                if (skipped < currentRequest.getProgress()) {
                    currentRequest.success();
                }
            }
            int bytesRead;
            logger.debug("Нужно передать: {} байт", input.available());
            long total = input.available();
            logger.trace("Принял {}", socket.receive().text()); // Синхронизация канала
            writeLong(total, clientAddress);
            long startProgress = currentRequest.getProgress();
            byte[] buffer = new byte[bufferSize];

            try (ProgressBar pb = new ProgressBar("Передача " + fileName, total + startProgress)) {
                pb.stepTo(startProgress);
                while ((bytesRead = input.read(buffer)) != -1) {
                    socket.send(Arrays.copyOf(buffer, bytesRead), clientAddress, port, sendTimeout);
                    currentRequest.addProgress(bytesRead);
                    pb.stepBy(bytesRead);
                }
            }
            logger.debug("Передача завершена");
            currentRequest.success();
            lastDownloadRequest = currentRequest;

        } catch (IOException e) {
            logger.error("Ошибка передачи: {}", e.getMessage());
            throw new IOException(e);
        }
    }

    public void uploadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException {
        var currentRequest = new UploadRequest(clientAddress, fileName);
        Path path = Paths.get(fileName).toAbsolutePath();
        Files.createDirectories(path.getParent());
        long existingSize = 0;
        var toStartFromZero = !currentRequest.equals(lastUploadRequest) || !cont || lastUploadRequest.isSuccessful();
        if(toStartFromZero) {
            if (lastUploadRequest != null && !lastUploadRequest.isSuccessful()) {
                Files.deleteIfExists(path);
            }
            lastUploadRequest = currentRequest;
        } else {
            currentRequest.addProgress(lastUploadRequest.getProgress());
            logger.debug("Continue");
        }
        try (FileOutputStream fos = new FileOutputStream(path.toFile(), !toStartFromZero);
             FileChannel channel = fos.getChannel()) {

            accept(clientAddress);
            socket.receive();
            long currentSize = 0;
            if(Files.exists(path)) {
                currentSize = Files.size(path);
            }
            if(cont) {
                writeLong(currentSize, clientAddress);
            }
            long extraSize = readLong();

            try (ProgressBar pb = new ProgressBarBuilder()
                    .setTaskName("Скачивание " + fileName)
                    .setInitialMax(extraSize)
                    .build()) {

                pb.stepTo(existingSize);

                if (existingSize < extraSize) {
                    transferFileWithProgress(channel, extraSize, existingSize, pb);
                }
                currentRequest.success();
                lastUploadRequest = currentRequest;

            }
            logger.debug(
                    "Файл {} скачан)",
                    path
            );
        }
    }

    private void transferFileWithProgress(
            FileChannel channel,
            long fileSize,
            long offset,
            ProgressBar pb
    ) throws IOException {
        Message buffer;
        long transferred = offset;
        pb.stepTo(offset);
        while (transferred < fileSize) {
            buffer = socket.receive(120_000);

            channel.write(ByteBuffer.wrap(buffer.data()), offset + transferred);
            transferred += buffer.length();

            pb.stepBy(buffer.length());
        }
    }

    private void accept(InetAddress address) throws IOException {
        socket.send(Status.SUCCESS.code() + " ACCEPT", address, port);
    }

    private long readLong() throws IOException {
        return ByteBuffer.wrap(socket.receive().data())
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
    }

    private void writeLong(long value, InetAddress address) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN); // 8 байт
        buffer.putLong(value).order(ByteOrder.BIG_ENDIAN);
        socket.send(buffer.array(), address, port);
    }
}
