package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;
import server.client.manager.UdpClientManager;
import server.downloader.UdpDownloader;
import server.socket.Message;
import server.socket.ReliableUdpSocket;
import server.status.Status;

import java.io.IOException;
import java.net.*;

public class UdpConnector extends Connector {
    private static final Logger logger = LoggerFactory.getLogger(UdpConnector.class);
    private final int port;
    private static final String CONNECT_MESSAGE = Status.CONNECT.code() + " CONNECT";

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
                    var message = accept(socket);
                    int freePort = findFreePort();
                    socket.send(Integer.toString(freePort), message.address(), message.port());
                    establishConnection("UDP", freePort);
                    socket.stopServices();
                } catch (SocketTimeoutException _) {
                    logger.debug("No clients connected (timeout) host");
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private Message accept(ReliableUdpSocket socket) throws IOException {
        var message = socket.receive();
        logger.info("Received message: {}", message.text());
        if (CONNECT_MESSAGE.equals(message.text())) {
            socket.send(Status.SUCCESS.code() + " ACCEPT", message.address(), message.port());
        }
        return message;
    }

    @Override
    public void connect() {
        logger.info("free port {}", port);
        try (var socket = new ReliableUdpSocket(port, true)) {
            socket.setSoTimeout(Connector.TIMEOUT);
            try {
                logger.info("socket port {}", socket.getPort());
                var message = accept(socket);
                logger.info("UDP connection established with client: {}", message.address());
                var udpDownloader = new UdpDownloader(socket, message.port(), 65507 - 9, 180_000);
                ClientManager udpClientManager = new UdpClientManager(udpDownloader, socket, message.address(), message.port());
                udpClientManager.communicate();
            } catch (SocketTimeoutException _) {
                logger.debug("No clients connected (timeout) port {}", this.port);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}
