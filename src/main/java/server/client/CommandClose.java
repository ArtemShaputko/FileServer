package server.client;

class CommandClose extends ClientManager.Command {

    public CommandClose(ClientManager manager) {
        manager.super();
    }

    @Override
    protected final void execute() {
        closeConnection();
        super.writeEndMessage();
    }
}
