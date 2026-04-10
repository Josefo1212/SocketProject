package pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

class Pool implements ConnectionPool {
    private final ArrayBlockingQueue<Connection> connectionPool;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    Pool(String driverClassName, String dbUrl, String dbUser, String dbPassword, int poolSize) throws SQLException {
        this.dbUrl = Objects.requireNonNull(dbUrl, "dbUrl");
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.connectionPool = new ArrayBlockingQueue<>(poolSize);

        loadDriver(driverClassName);

        for (var i = 0; i < poolSize; i++) {
            connectionPool.add(createConnection());
        }
    }


    private static void loadDriver(String driver) throws SQLException {
        if (driver == null || driver.isBlank()) {
            throw new SQLException("Driver JDBC vacío. El adapter debe proveer driverClassName.");
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontró el driver JDBC: " + driver, e);
        }
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private boolean isUsable(Connection connection) {
        if (connection == null) return false;
        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public Connection getConnection() throws InterruptedException {
        var timeout = Config.getLong("POOL_TIMEOUT");
        Connection c = connectionPool.poll(timeout, TimeUnit.MILLISECONDS);
        if (c == null) return null;
        if (isUsable(c)) return c;

        try {
            return createConnection();
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public void releaseConnection(Connection connection) {
        if (connection == null) {
            return;
        }

        if (isUsable(connection)) {
            connectionPool.offer(connection);
            return;
        }

        // Si la conexión viene inválida/cerrada, intentamos reponer un slot del pool.
        try {
            connectionPool.offer(createConnection());
        } catch (SQLException ignored) {
        }
    }

    void closePool() throws SQLException {
        for (var connection : connectionPool) {
            connection.close();
        }
    }
}
