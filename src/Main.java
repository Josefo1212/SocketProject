import Client.ClientUI;
import DB.adapters.DatabaseType;
import DB.dbcomponent.DBComponent;
import DB.dbcomponent.DBComponentConnector;
import DB.dbcomponent.DBComponentConnector.ConnectResult;
import DB.dbcomponent.DBException;
import Server.GameServer;

public class Main {
    public static void main() {
        try {
            DBComponentConnector connector = new DBComponentConnector();
            ConnectResult result = connector.connect(
                DatabaseType.POSTGRES,
                "localhost",
                5432,
                "SocketProject",
                "socket_user",
                "socket_pass_123"
            );
            GameServer server = new GameServer(result.component());
            server.start();
            javax.swing.SwingUtilities.invokeLater(() -> ClientUI.createAndShow("Jugador 1", 0));
        } catch (DBException e) {
            System.err.println("Error al conectar con la base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
