package server.client.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.client.manager.ClientManager;
import server.status.Status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CommandTime extends ClientManager.Command {
    private static final Logger logger = LoggerFactory.getLogger(CommandPing.class);

    public CommandTime(ClientManager manager) {
        manager.super();
    }

    @Override
    protected final void execute() {
       writeMessage(Status.SUCCESS.code(), LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("E d MMMM yyyy, HH:mm:ss", Locale.forLanguageTag("ru"))));
        super.writeEndMessage();
    }
}
