package server.client.manager;

import server.download.TcpDownloader;
import server.status.Status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpClientManager extends ClientManager {
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final Socket socket;
    public TcpClientManager(TcpDownloader tcpDownloader, PrintWriter writer, BufferedReader reader, Socket socket) {
        super(tcpDownloader);
        this.writer = writer;
        this.reader = reader;
        this.socket = socket;
        this.clientAddress = socket.getInetAddress();
    }

    @Override
    public boolean checkChannel() {
        return socket.isConnected() && !socket.isOutputShutdown() && !socket.isInputShutdown();
    }

    @Override
    protected void writeMessage(int code, String message) {
        if (writer != null) {
            writer.println(code + " " + message);
        }
    }
    @Override
    protected void writeEndMessage() {
        if (writer != null) {
            writer.println(Status.END.code() + " END");
        }
    }

    @Override
    protected void writeHeartbeatResponse() {
        if (writer != null) {
            writer.println(ClientManager.HEARTBEAT_RESPONSE);
        }
    }

    @Override
    protected void writeHeartbeatRequest() {
        if (writer != null) {
            writer.println(ClientManager.HEARTBEAT_REQUEST);
        }
    }

    @Override
    protected String readLine() throws IOException {
        return reader.readLine();
    }
}
