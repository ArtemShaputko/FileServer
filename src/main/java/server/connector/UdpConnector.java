package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;
import server.client.manager.UdpClientManager;
import server.downloader.UdpDownloader;
import server.socket.ReliableUdpSocket;

import java.net.*;

public class UdpConnector implements Connector {
    private static final Logger logger = LoggerFactory.getLogger(UdpConnector.class);
    private final int port;/*
    private static final String ACCEPT_MESSAGE = Status.SUCCESS.code() + " ACCEPT";
    public static final int BUFFER_SIZE = 1024;*/

    public UdpConnector(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        logger.info("Starting UDP server");
        try (var socket = new ReliableUdpSocket(port)) {
            socket.setSoTimeout(Connector.TIMEOUT);
            while (true) {
                try {
                    socket.startServices();
                    var message = socket.receive();
                    logger.info("UDP connection established with client: {}", message.address());
                    var udpDownloader = new UdpDownloader(socket, message.port(), 65507 - 9, 180_000);
                    ClientManager udpClientManager = new UdpClientManager(udpDownloader, socket, message.address(), message.port());
                    udpClientManager.communicate();
                    socket.stopServices();
                } catch (SocketTimeoutException _) {
                    logger.debug("No clients connected (timeout)");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
    }
}
