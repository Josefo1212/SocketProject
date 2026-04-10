package pool;

import java.sql.Connection;

public interface ConnectionPool {
    Connection getConnection() throws InterruptedException;

    void releaseConnection(Connection connection);
}

