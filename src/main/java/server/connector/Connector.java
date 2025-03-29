package server.connector;

import server.Main;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

public abstract class Connector {
    public static int HEARTBEAT_LIMIT = 3;
    public static int TIMEOUT = 60_000;
    public abstract void start();
    public abstract void connect();

    protected static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    protected static void establishConnection(String protocol, int port) throws IOException {
        String jarPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        jarPath = java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8);

        // Формируем аргументы для нового процесса
        String[] cmdArgs = {"java", "-jar", jarPath, protocol ,String.valueOf(port)};

        // Запускаем процесс
        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.start();
    }
}
