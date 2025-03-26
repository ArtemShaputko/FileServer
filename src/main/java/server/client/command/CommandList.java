package server.client.command;

import server.client.manager.ClientManager;
import server.status.Status;

import java.io.File;

public class CommandList extends ClientManager.Command {
    public CommandList(ClientManager clientManager) {
        clientManager.super();
    }

    @Override
    protected void execute() {
        File directory = new File("./download"); // Укажите путь к каталогу

        File[] files = directory.listFiles(); // Получаем массив файлов
        StringBuilder builder = new StringBuilder()
                .append("Содерживое корневого каталога:\n");

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    builder.append("\tФайл: ");
                } else if (file.isDirectory()) {
                    builder.append("\tКаталог: ");
                }
                builder.append(file.getName()).append("\n");
            }
            writeMessage(Status.SUCCESS.code(), builder.toString());
        } else {
            writeMessage(Status.SUCCESS.code(), "Каталог пуст.");
        }
        super.writeEndMessage();
    }
}
