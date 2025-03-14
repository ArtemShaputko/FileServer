package server.client;

import server.status.Status;

import java.net.InetAddress;

public class CommandDownload extends ClientManager.Command {

    private final String line;
    private final InetAddress clientAddress;

    CommandDownload(ClientManager manager, String line, InetAddress clientAddress) {
        manager.super();
        COMMAND_LENGTH = 8;
        this.line = line.trim();
        this.clientAddress = clientAddress;
    }

    @Override
    protected final void execute() {
        var args = line.split(" ");
        boolean cont = args.length > 3 && args[3].equalsIgnoreCase("continue");
        super.writeEndMessage();
        if(args.length < 2) {
            writeMessage(Status.ERROR.code(), "DOWNLOAD: Нет имени файла");
        }
        downloadFile(args[1], clientAddress, cont);
    }
}
