package server;

import server.connector.Connector;
import server.connector.TcpConnector;
import server.connector.UdpConnector;

import java.util.Scanner;

public class Main {
        public static void main(String[] args) {
            Connector connector;
            if(args.length > 1) {
                int port = Integer.parseInt(args[1]);
                if (args[0].equals("TCP")) {
                    System.out.println("TCP, port " + port);
                    connector = new TcpConnector(port);
                } else {
                    System.out.println("UDP, port " + port);
                    connector = new UdpConnector(port);
                }
                connector.connect();
            } else {
                Scanner scanner = new Scanner(System.in);
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

/*    public static void main(String[] args) throws IOException {
        int cur;
        if (args.length == 0) {
            cur = 1; // Стартовое значение
        } else {
            cur = Integer.parseInt(args[0]); // Берем первый (и единственный) аргумент
        }

        System.out.println("Текущее значение: " + cur);
        System.out.println("Все аргументы: " + Arrays.toString(args));

        if (cur > 5) {
            System.out.println("Завершение (cur > 5)");
            return;
        }

        // Получаем путь к JAR-файлу (с декодированием пробелов)
        String jarPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");

        // Формируем аргументы для нового процесса
        String[] cmdArgs = {"java", "-jar", jarPath, String.valueOf(cur + 1)};

        // Запускаем процесс
        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        System.out.println("Новый PID: " + process.pid());
    }*/
}
