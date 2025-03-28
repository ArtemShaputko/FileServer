package server.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Connector {
    int HEARTBEAT_LIMIT = 3;
    int TIMEOUT = 60_000;
    void start();
}
