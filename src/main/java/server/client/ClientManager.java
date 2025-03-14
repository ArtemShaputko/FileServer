package server.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;
import server.download.Downloader;
import server.status.Status;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Optional;

public class ClientManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    public static final String HEARTBEAT_REQUEST = "PING";
    public static final String HEARTBEAT_RESPONSE = "PONG";
    private boolean isConnected = false;
    private PrintWriter writer;
    private final Downloader downloader;
    private int heartbeatTimes;
    long startTime;
    InetAddress clientAddress;

    public ClientManager(Downloader downloader) {
        this.downloader = downloader;
    }

    abstract class Command {
        protected int COMMAND_LENGTH;

        protected abstract void execute();

        protected void writeMessage(int code, String message) {
            ClientManager.this.writeMessage(code, message);
        }

        protected void writeEndMessage() {
            ClientManager.this.writeEndMessage();
        }

        protected void writeHeartbeatResponse() {
            writer.write(HEARTBEAT_RESPONSE);
        }

        protected void closeConnection() {
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
                logger.error(e.getMessage());
            }
        }
    }

    public void communicate(Socket clientSocket) throws IOException {
        InputStream input = clientSocket.getInputStream();
        clientAddress = clientSocket.getInetAddress();
        var output = clientSocket.getOutputStream();
        downloader.setIn(input);
        downloader.setOut(output);
        writer = new PrintWriter(output, true);
        startTime = System.currentTimeMillis();
        try (var reader = new BufferedReader(new InputStreamReader(input))) {
            isConnected = true;
            while (isConnected) {
                try {
                    String line = reader.readLine();
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
        }
        writer.close();
    }

    public void heartbeat() {
        logger.debug("Нет ответа от клиента {}", clientAddress.getHostAddress());
        if(heartbeatTimes < Server.HEARTBEAT_LIMIT) {
            logger.debug("Отправляю heartbeat message");
            writer.println(HEARTBEAT_REQUEST);
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

    private void writeMessage(int code, String message) {
        if (writer != null) {
            writer.println(code + " " + message);
        }
    }

    private void writeEndMessage() {
        if (writer != null) {
            writer.println(Status.END.code() + " END");
        }
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
