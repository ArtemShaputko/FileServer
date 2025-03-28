package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;
import server.client.manager.TcpClientManager;
import server.downloader.TcpDownloader;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpConnector extends Connector {
    private static final Logger logger = LoggerFactory.getLogger(TcpConnector.class);
    private final int port;

    public TcpConnector(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        logger.info("Starting TCP Connector");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     var writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    int freePort = findFreePort();
                    writer.println(freePort);
                    establishConnection("TCP", freePort);
                } catch (Exception e) {
                    logger.error("Превышено время ожидания, автоматическое отключение {} HOST", e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void connect() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            TcpDownloader tcpDownloader = new TcpDownloader();
            try (Socket clientSocket = serverSocket.accept();
                 var reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 var writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                tcpDownloader.setIn(clientSocket.getInputStream());
                tcpDownloader.setOut(clientSocket.getOutputStream());
                ClientManager manager = new TcpClientManager(tcpDownloader, writer, reader, clientSocket);
                logger.info("Подключился клиент {}", clientSocket.getRemoteSocketAddress());
                clientSocket.setKeepAlive(true);
                clientSocket.setSoTimeout(TIMEOUT);
                manager.communicate();
                logger.info("Отключился от клиента {}", clientSocket.getRemoteSocketAddress());
            } catch (Exception e) {
                logger.error("Превышено время ожидания, автоматическое отключение {}, port = {}", e.getMessage(), port);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

}
