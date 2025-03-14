package server.download;

import java.net.InetAddress;
import java.util.Objects;

public class UploadRequest {
    private final InetAddress clientAddress;
    private final String fileName;
    private long progress;
    private boolean isSuccessful = false;

    public UploadRequest(InetAddress clientAddress, String fileName) {
        this.clientAddress = clientAddress;
        this.fileName = fileName;
        this.progress = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadRequest that = (UploadRequest) o;
        return Objects.equals(clientAddress, that.clientAddress) && Objects.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientAddress, fileName);
    }

    public void addProgress(long progress) {
        this.progress += progress;
    }

    public long getProgress() {
        return progress;
    }

    public InetAddress getClientAddress() {
        return clientAddress;
    }

    public String getFileName() {
        return fileName;
    }

    public void success() {
        isSuccessful = true;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }
}

