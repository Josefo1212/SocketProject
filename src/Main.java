import Client.ClientUI;
import Client.UiTheme;
import DB.adapters.DatabaseType;
import DB.dbcomponent.DBComponentConnector;
import DB.dbcomponent.DBComponentConnector.ConnectResult;
import DB.dbcomponent.DBException;
import Server.GameServer;
import pool.Config;

import javax.swing.SwingUtilities;

final int CLIENT_COUNT = 2;
final int SERVER_PORT = 5000;

void main() {
    UiTheme.installGlobalLookAndFeel();
    startServerInBackground();
    launchClientInterfaces();
}

void launchClientInterfaces() {
    SwingUtilities.invokeLater(() -> {
        for (int i = 1; i <= CLIENT_COUNT; i++) {
            int offset = (i - 1) * 40;
            try {
                ClientUI.createAndShow("Jugador " + i, offset);
            } catch (RuntimeException e) {
                System.err.println("No se pudo abrir la interfaz del cliente " + i + ": " + rootCauseMessage(e));
            }
        }
    });
}

void startServerInBackground() {
    Thread serverBootstrap = new Thread(() -> bootServer(), "server-bootstrap");
    serverBootstrap.setDaemon(true);
    serverBootstrap.start();
    try {
        Thread.sleep(350);
    } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
    }
}

void bootServer() {
    try {
        String dbHost = Config.getRequired("DB_HOST");
        int dbPort = Config.getInt("DB_PORT");
        String dbName = Config.getRequired("DB_NAME");
        String dbUser = Config.getRequired("DB_USER");
        String dbPassword = Config.getRequired("DB_PASSWORD");

        DBComponentConnector connector = new DBComponentConnector();
        ConnectResult result = connector.connect(
            DatabaseType.POSTGRES,
            dbHost,
            dbPort,
            dbName,
            dbUser,
            dbPassword
        );
        GameServer server = new GameServer(result.component(), SERVER_PORT);
        server.start();
    } catch (IllegalStateException e) {
        System.err.println("Error de configuración: " + e.getMessage());
    } catch (DBException e) {
        System.err.println("No se pudo iniciar el servidor (DB): " + e.getMessage());
    } catch (RuntimeException e) {
        // Incluye fallos en inicializacion del pool JDBC (Connection refused, driver, etc.)
        System.err.println("No se pudo iniciar el servidor: " + rootCauseMessage(e));
    }
}

String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
        current = current.getCause();
    }
    String message = current.getMessage();
    return (message == null || message.isBlank()) ? current.toString() : message;
}
