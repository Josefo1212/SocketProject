import DB.adapters.DatabaseType;
import dbcomponent.DBComponentConnector;
import dbcomponent.DBComponentConnector.ConnectResult;
import dbcomponent.DBException;
import Server.GameServer;

void main() {
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
    } catch (DBException e) {
        System.err.println("Error al conectar con la base de datos: " + e.getMessage());
        e.printStackTrace();
    }
}
