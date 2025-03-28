package server.client.manager;

import server.downloader.UdpDownloader;
import server.socket.ReliableUdpSocket;

import java.io.IOException;
import java.net.InetAddress;

public class UdpClientManager extends ClientManager {
    private final ReliableUdpSocket socket;
    private final int clientPort;

    public UdpClientManager(UdpDownloader downloader, ReliableUdpSocket socket, InetAddress clientAddress, int clientPort) {
        super(downloader);
        this.socket = socket;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
    }

    @Override
    public boolean checkChannel() {
        return true;
    }

    @Override
    protected void writeMessage(int code, String message) {
        try {
            socket.send(code + " " + message, clientAddress, clientPort);
        } catch (NullPointerException | IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    protected void writeEndMessage() {
        writeMessage(300, "END");
    }

    @Override
    protected void writeHeartbeatResponse() {
        try {
            socket.send(ClientManager.HEARTBEAT_RESPONSE, clientAddress, clientPort);
        } catch (NullPointerException | IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    protected void writeHeartbeatRequest() {
        try {
            socket.send(ClientManager.HEARTBEAT_REQUEST, clientAddress, clientPort);
        } catch (NullPointerException | IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    protected String readLine() throws IOException {
        var message = socket.receive();
        logger.debug("Message: {}",message.text());
        return message.text();
    }
}
