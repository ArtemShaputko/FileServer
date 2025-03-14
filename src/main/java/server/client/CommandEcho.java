package server.client;

import server.status.Status;

public class CommandEcho extends ClientManager.Command {
    private final String line;

    public CommandEcho(ClientManager manager, String line) {
        manager.super();
        this.line = line;
        COMMAND_LENGTH = 4;
    }

    @Override
    protected final void execute() {
        writeMessage(Status.SUCCESS.code(),line.substring(COMMAND_LENGTH).trim());
        super.writeEndMessage();
    }
}
