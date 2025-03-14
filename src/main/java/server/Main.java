package server;

public class Main {
    public static void main(String[] args) {
        Server server = new Server(12345);
        server.start();
    }
}
