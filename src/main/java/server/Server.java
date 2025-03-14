package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.*;
import server.download.Downloader;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private final int port;
    public static final int TIMEOUT = 20_000;
    public static final int HEARTBEAT_LIMIT = 3;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Downloader downloader = new Downloader();
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     ClientManager manager = new ClientManager(downloader)
                ) {
                    logger.info("Подключился клиент {}", clientSocket.getRemoteSocketAddress());
                    clientSocket.setKeepAlive(true);
                    clientSocket.setSoTimeout(TIMEOUT);
                    manager.communicate(clientSocket);
                    logger.info("Отключился от клиента {}", clientSocket.getRemoteSocketAddress());
                } catch (Exception e) {
                    logger.error("Превышено время ожидания, автоматическое отключение {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

}
