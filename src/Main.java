import Client.ClientUI;
import DB.adapters.DatabaseType;
import DB.dbcomponent.DBComponentConnector;
import DB.dbcomponent.DBComponentConnector.ConnectResult;
import DB.dbcomponent.DBException;
import Server.GameServer;
import pool.Config;

import javax.swing.SwingUtilities;

final int CLIENT_COUNT = 2;

void main() {
    try {
        String dbHost = Config.get("DB_HOST");
        int dbPort = Config.getInt("DB_PORT");
        String dbName = Config.get("DB_NAME");
        String dbUser = Config.get("DB_USER");
        String dbPassword = Config.get("DB_PASSWORD");

        DBComponentConnector connector = new DBComponentConnector();
        ConnectResult result = connector.connect(
            DatabaseType.POSTGRES,
            dbHost,
            dbPort,
            dbName,
            dbUser,
            dbPassword
        );
        GameServer server = new GameServer(result.component());
        server.start();

        SwingUtilities.invokeLater(() -> {
            for (int i = 1; i <= CLIENT_COUNT; i++) {
                int offset = (i - 1) * 40;
                ClientUI.createAndShow("Jugador " + i, offset);
            }
        });
    } catch (DBException e) {
        System.err.println("Error al conectar con la base de datos: " + e.getMessage());
        e.printStackTrace();
    }
}

