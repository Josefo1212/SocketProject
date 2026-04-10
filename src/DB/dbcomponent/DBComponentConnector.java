package DB.dbcomponent;

import DB.adapters.DBAdapterFactory;
import DB.adapters.DatabaseType;
import DB.adapters.IDBAdapter;

/**
 * Servicio de conexión para desacoplar la UI de la lógica de creación del DBComponent.
 */
public final class DBComponentConnector {

    private static final DBQueryId DEFAULT_PING_QUERY = new DBQueryId("usuario.selectOne");

    public ConnectResult connect(DatabaseType type,
                                 String host,
                                 int port,
                                 String dbName,
                                 String user,
                                 String password) throws DBException {
        return connect(type, host, port, dbName, user, password, defaultQueriesLocation(type));
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

        // Verificación temprana de conectividad y queries predefinidas.
        component.query(DEFAULT_PING_QUERY);

        return new ConnectResult(type, cfg, normalizedQueriesLocation, component);
    }

    private String defaultQueriesLocation(DatabaseType type) {
        if (type == null) {
            throw new IllegalArgumentException("DatabaseType no puede ser null");
        }
        return switch (type) {
            case POSTGRES -> "classpath:/db/queries-postgres.properties";
            case MYSQL -> "classpath:/db/queries-mysql.json";
        };
    }

    private String normalizeQueriesLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("queriesLocation no puede ser null/vacío");
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
