package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Connector {
    Logger logger = LoggerFactory.getLogger(Connector.class);
    int HEARTBEAT_LIMIT = 3;
    int TIMEOUT = 40_000;
    void start();
}
