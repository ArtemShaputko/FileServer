package server;

import server.connector.Connector;
import server.connector.TcpConnector;
import server.connector.UdpConnector;

import java.util.Scanner;

public class Main {
        public static void main(String[] args) {
            Scanner scanner = new Scanner(System.in);
            Connector connector;
            while (true) {
                System.out.print("Insert type of server:\n(UDP/TCP)> ");
                var answer = scanner.nextLine();
                if ("UDP".equals(answer)) {
                    connector = new UdpConnector(12345);
                } else if ("TCP".equals(answer)) {
                    connector = new TcpConnector(12345);
                } else {
                    continue;
                }
                break;
            }
            connector.start();
        }

}
