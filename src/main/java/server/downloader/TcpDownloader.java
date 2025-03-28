package server.downloader;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.status.Status;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

public class TcpDownloader implements Downloader {
    private static final Logger logger = LoggerFactory.getLogger(TcpDownloader.class);
    private DownloadRequest lastDownloadRequest = null;
    private UploadRequest lastUploadRequest = null;
    private OutputStream out;
    private InputStream in;
    private final int byteBuffer = 8192;

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public void setIn(InputStream in) {
        this.in = in;
    }

    public OutputStream getOut() {
        return out;
    }

    public InputStream getIn() {
        return in;
    }

    public TcpDownloader() {
    }

    public TcpDownloader(OutputStream out, InputStream in) {
        this.out = out;
        this.in = in;
    }

    public void downloadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException {
        var currentRequest = new DownloadRequest(clientAddress, fileName);
        var dos = new DataOutputStream(out);
        var dis = new DataInputStream(in);
        try (var input = new FileInputStream(fileName)) {
            if (!cont || !currentRequest.equals(lastDownloadRequest) || lastDownloadRequest.isSuccessful()) {
                if(cont) {
                    dos.write("Невозможно продолжить, нет подходящих данных сессии\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                lastDownloadRequest = currentRequest;
                logger.debug("{} Accept", Status.SUCCESS);
                accept();
            } else {
                accept();
                currentRequest.addProgress(Long.reverseBytes(dis.readLong()));
                logger.debug("Continue");
                long skipped = input.skip(currentRequest.getProgress());
                if (skipped < currentRequest.getProgress()) {
                    currentRequest.success();
                }
            }
            int bytesRead;
            logger.debug("Нужно передать: {} байт",input.available());
            long total = input.available();
            dis.read(); // Синхронизация канала
            dos.writeLong(Long.reverseBytes(total));
            dos.flush();
            long startProgress = currentRequest.getProgress();
            byte[] buffer = new byte[byteBuffer];

            try(ProgressBar pb = new ProgressBar("Передача " + fileName, total + startProgress)) {
            pb.stepTo(startProgress);
                while ((bytesRead = input.read(buffer)) != -1) {
                    int finalBytesRead = bytesRead;
                    CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                        try {
                            dos.write(buffer, 0, finalBytesRead);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    });
                    try {
                        writeFuture.get(120_000, TimeUnit.MILLISECONDS);
                        currentRequest.addProgress(bytesRead);
                        pb.stepBy(bytesRead);
                    } catch (TimeoutException e) {
                        writeFuture.cancel(true);
                        throw new SocketException("Таймаут записи блока данных");
                    }
                }
            }
            logger.debug("Передача завершена");
            currentRequest.success();
            lastDownloadRequest = currentRequest;

        } catch (IOException | ExecutionException | InterruptedException  e){
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            logger.error("Ошибка передачи: {}", cause.getMessage());
            throw new CompletionException(cause);
        }finally {
            dos.flush();
        }
    }

    public void uploadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException {
        var currentRequest = new UploadRequest(clientAddress, fileName);
        Path path = Paths.get(fileName).toAbsolutePath();
        Files.createDirectories(path.getParent());
        long existingSize = 0;
        var dis = new DataInputStream(in);
        var dos = new DataOutputStream(out);
        var toStartFromZero = !currentRequest.equals(lastUploadRequest) || !cont || lastUploadRequest.isSuccessful();
        if(toStartFromZero) {
            if (lastUploadRequest != null && !lastUploadRequest.isSuccessful()) {
                Files.deleteIfExists(path);
            }
            lastUploadRequest = currentRequest;
        } else {
            currentRequest.addProgress(lastUploadRequest.getProgress());
            System.out.println("Continue");
        }
        try (FileOutputStream fos = new FileOutputStream(path.toFile(), !toStartFromZero);
             FileChannel channel = fos.getChannel()) {

            accept();
            dis.read();
            long currentSize = 0;
            if(Files.exists(path)) {
                currentSize = Files.size(path);
            }
            if(cont) {
                dos.writeLong(Long.reverseBytes(currentSize));
                dos.flush();
            }
            long extraSize = Long.reverseBytes(dis.readLong());

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

    private void accept() throws IOException {
        out.write((Status.SUCCESS.code() + " ACCEPT\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void transferFileWithProgress(
            FileChannel channel,
            long fileSize,
            long offset,
            ProgressBar pb
    ) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(byteBuffer);
        long transferred = offset;

        pb.stepTo(offset);

        while (transferred < fileSize) {
            int read = in.read(buffer.array());
            if (read == -1) break;

            buffer.limit(read);
            channel.write(buffer, transferred);
            buffer.clear();
            transferred += read;

            pb.stepBy(read);
        }
    }
}
