package server.client.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;

public class CommandPing extends ClientManager.Command {
    private static final Logger logger = LoggerFactory.getLogger(CommandPing.class);

    public CommandPing(ClientManager manager) {
        manager.super();
    }

    @Override
    protected final void execute() {
        logger.debug("Received heartbeat request from client");
        writeHeartbeatResponse();
    }
}
