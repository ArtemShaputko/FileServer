package server.client;

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
                        \t\t- file_name - скачать файл с сервера
                        \t\t- file_name1 file_name2 - скачать с сервера файл №1 под именем №2
                        \t\t- file_name1 file_name2 continue - продолжить загрузку
                        \t> ECHO string - вернуть строку
                        \t> TIME - показать строку
                        \t> PING - окликнуть сервер""";
        writeMessage(Status.SUCCESS.code(), message);
        super.writeEndMessage();
    }
}
