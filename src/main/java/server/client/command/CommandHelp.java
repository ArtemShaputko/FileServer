package server.client.command;

import server.client.manager.ClientManager;
import server.status.Status;

public class CommandHelp extends ClientManager.Command {

    public CommandHelp(ClientManager clientManager) {
        clientManager.super();
    }

    @Override
    protected void execute() {
        String message =
                """
                        HELP:
                        \t> CLOSE - закрыть соедингение и покинуть сервер
                        \t> DOWNLOAD:
                        \t\t- file_name1 file_name2 - скачать с сервера файл №1 под именем №2
                        \t\t- file_name1 file_name2 -continue - продолжить загрузку
                        \t> ECHO string - вернуть строку
                        \t> TIME - показать строку
                        \t> UPLOAD:
                        \t\t- file_name1 file_name2 - загрузить на сервер файл №1 под именем №2
                        \t\t- file_name1 file_name2 -continue - продолжить загрузку""";
        writeMessage(Status.SUCCESS.code(), message);
        super.writeEndMessage();
    }
}
