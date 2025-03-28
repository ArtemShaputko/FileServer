package server.downloader;

import java.io.IOException;
import java.net.InetAddress;

public interface Downloader {
    void downloadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException;
    void uploadFile(String fileName, InetAddress clientAddress, boolean cont) throws IOException;
}
