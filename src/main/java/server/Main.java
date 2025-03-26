package server;

import server.connector.Connector;
import server.connector.UdpConnector;

public class Main {
    public static void main(String[] args) {
        Connector connector = new UdpConnector(12345);
        connector.start();
    }
}
