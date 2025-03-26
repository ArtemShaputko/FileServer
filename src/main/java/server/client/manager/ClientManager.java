package server.client.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.connector.Connector;
import server.client.command.*;
import server.download.Downloader;
import server.status.Status;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Optional;

public abstract class ClientManager {
    protected static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    public static final String HEARTBEAT_REQUEST = "PING";
    public static final String HEARTBEAT_RESPONSE = "PONG";
    private boolean isConnected = false;

    private final Downloader downloader;
    private int heartbeatTimes;
    long startTime;
    InetAddress clientAddress;

    public ClientManager(Downloader downloader) {
        this.downloader = downloader;
    }

    public abstract class Command {
        protected int COMMAND_LENGTH;

        protected abstract void execute();

        protected void writeMessage(int code, String message) {
            ClientManager.this.writeMessage(code, message);
        }

        protected void writeEndMessage() {
            ClientManager.this.writeEndMessage();
        }

        protected void writeHeartbeatResponse() {
            ClientManager.this.writeHeartbeatResponse();
        }

        protected void closeConnection() {
            logger.info("Closing connection");
            isConnected = false;
        }

        protected void downloadFile(String fileName, InetAddress address, boolean cont) {
            try {
                downloader.downloadFile(fileName, address, cont);
            } catch (FileNotFoundException e) {
                logger.error("Файл {} не найден", fileName);
                writeMessage(Status.ERROR.code(), "Файл " + fileName + " не найден");
            } catch (IOException e) {
                logger.error(e.getMessage());
                closeConnection();
            }
        }

        protected void uploadFile(String fileName, InetAddress address, boolean cont) {
            try {
                downloader.uploadFile(fileName, address, cont);
            } catch (IOException e) {
                logger.error(e.getMessage());
                closeConnection();
            }
        }
    }

    public void communicate() throws IOException {
        startTime = System.currentTimeMillis();
        isConnected = true;
        while (checkChannel() && isConnected) {
            try {
                String line = readLine();
                if (line != null) {
                    startTime = System.currentTimeMillis();
                    String trimmedLine = line.trim();
                    String[] commandArray = trimmedLine.split(" ", 2);
                    getCommand(commandArray[0], trimmedLine).ifPresentOrElse(
                            Command::execute,
                            () -> {
                                writeMessage(Status.SUCCESS.code(), "Нет такой команды: " + commandArray[0]);
                                writeEndMessage();
                            });
                }
            } catch (SocketTimeoutException e) {
                heartbeat();
            }
        }
        logger.info("Connection with client {} closed", clientAddress);
    }

    public abstract boolean checkChannel();

    public void heartbeat() {
        logger.debug("Нет ответа от клиента {}", clientAddress.getHostAddress());
        if (heartbeatTimes < Connector.HEARTBEAT_LIMIT) {
            logger.debug("Отправляю heartbeat message");
            writeHeartbeatRequest();
            heartbeatTimes++;
        } else {
            logger.error("Превышен порог ожидания, отключаюсь");
            isConnected = false;
        }
    }


    private Optional<Command> getCommand(String command, String line) {
        if (command.equals(HEARTBEAT_REQUEST)) {
            return Optional.of(new CommandPing(this));
        }
        return switch (command.toLowerCase()) {
            case "echo" -> Optional.of(new CommandEcho(this, line));
            case "close" -> Optional.of(new CommandClose(this));
            case "time" -> Optional.of(new CommandTime(this));
            case "help" -> Optional.of(new CommandHelp(this));
            case "list" -> Optional.of(new CommandList(this));
            case "download" -> Optional.of(new CommandDownload(this, line, clientAddress));
            case "upload" -> Optional.of(new CommandUpload(this, line, clientAddress));
            default -> Optional.empty();
        };
    }

    protected abstract void writeMessage(int code, String message);

    protected abstract void writeEndMessage();

    protected abstract void writeHeartbeatResponse();
    protected abstract void writeHeartbeatRequest();
    protected abstract String readLine() throws IOException;
}
