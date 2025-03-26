package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;
import server.client.manager.UdpClientManager;
import server.download.UdpDownloader;
import server.socket.ReliableUdpSocket;
import server.status.Status;

import java.io.IOException;
import java.net.*;

public class UdpConnector implements Connector {
    private static final Logger logger = LoggerFactory.getLogger(UdpConnector.class);
    private final int port;
    private static final String ACCEPT_MESSAGE = Status.SUCCESS.code() + " ACCEPT";
    public static final int BUFFER_SIZE = 1024;

    public UdpConnector(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        try (var socket = new ReliableUdpSocket(port, BUFFER_SIZE)) {
            socket.setSoTimeout(Connector.TIMEOUT);
            while (true) {
                try {
                    var message = socket.receive();
                    socket.setSoTimeout(Connector.TIMEOUT);
                    logger.info("UDP connection established with client: {}", message.address());
                    ClientManager udpClientManager = new UdpClientManager(
                            new UdpDownloader(socket,
                            message.port(),
                            BUFFER_SIZE), socket, message.address(), message.port());
                    udpClientManager.communicate();
                    socket.setSoTimeout(0);
                } catch (SocketTimeoutException _) {
                    logger.debug("No clients connected (timeout)");
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
