package DB.dbcomponent;

import DB.adapters.DBAdapterFactory;
import DB.adapters.DatabaseType;
import DB.adapters.IDBAdapter;

/**
 * Servicio de conexión para desacoplar la UI de la lógica de creación del DBComponent.
 */
public final class DBComponentConnector {

    public ConnectResult connect(DatabaseType type,
                                 String host,
                                 int port,
                                 String dbName,
                                 String user,
                                 String password) throws DBException {
        return connect(type, host, port, dbName, user, password, null);
    }

    public ConnectResult connect(DatabaseType type,
                                 String host,
                                 int port,
                                 String dbName,
                                 String user,
                                 String password,
                                 String queriesLocation) throws DBException {
        if (type == null) {
            throw new DBException(DBException.Category.CONFIG, null, "DatabaseType no puede ser null");
        }
        if (host == null || host.isBlank()) {
            throw new DBException(DBException.Category.CONFIG, null, "host no puede ser null o vacio");
        }
        if (port <= 0) {
            throw new DBException(DBException.Category.CONFIG, null, "port debe ser mayor que 0");
        }
        if (dbName == null || dbName.isBlank()) {
            throw new DBException(DBException.Category.CONFIG, null, "dbName no puede ser null o vacio");
        }
        if (user == null || user.isBlank()) {
            throw new DBException(DBException.Category.CONFIG, null, "user no puede ser null o vacio");
        }
        if (password == null) {
            throw new DBException(DBException.Category.CONFIG, null, "password no puede ser null");
        }

        IDBAdapter adapter = DBAdapterFactory.adapter(type);
        ConnectionConfig cfg = adapter.toConnectionConfig(host, port, dbName, user, password);
        String normalizedQueriesLocation = normalizeQueriesLocation(queriesLocation);

        DBComponent component = new DBComponent(
                cfg.driverClassName(),
                cfg.url(),
                cfg.user(),
                cfg.password(),
                normalizedQueriesLocation
        );

        // Verificación temprana mínima del estado de conexión.
        if (!component.isConnected()) {
            throw new DBException(DBException.Category.CONNECTION, null,
                    "No se pudo inicializar la conexión del DBComponent");
        }

        return new ConnectResult(type, cfg, normalizedQueriesLocation, component);
    }

    private String normalizeQueriesLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return location;
        }
        String normalized = location.startsWith("/") ? location : "/" + location;
        return "classpath:" + normalized;
    }

    public record ConnectResult(
            DatabaseType type,
            ConnectionConfig config,
            String queriesLocation,
            DBComponent component
    ) {
    }
}
