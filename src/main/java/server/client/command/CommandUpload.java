package server.client.command;

import server.client.manager.ClientManager;
import server.status.Status;

import java.net.InetAddress;

public class CommandUpload extends ClientManager.Command {
    private final String line;
    private final InetAddress clientAddress;

    public CommandUpload(ClientManager manager, String line, InetAddress clientAddress) {
        manager.super();
        COMMAND_LENGTH = 8;
        this.line = line.trim();
        this.clientAddress = clientAddress;
    }

    @Override
    protected void execute() {
        var args = line.split(" ");
        super.writeEndMessage();
        if (args.length < 2) {
            writeMessage(Status.ERROR.code(), "Нет имени файла");
        }
        boolean cont = args.length > 3 && args[3].equalsIgnoreCase("continue");
        uploadFile("upload/" + args[2], clientAddress, cont);
    }

}
