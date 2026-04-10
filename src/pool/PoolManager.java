package pool;

import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PoolManager {
    private static final ConcurrentHashMap<String, PoolManager> INSTANCES = new ConcurrentHashMap<>();
    private final String instanceKey;
    private final ConnectionPool pool;

    private PoolManager(String driverClassName, String url, String user, String password) {
        this.instanceKey = driverClassName + "|" + url + "|" + user;
        try {
            pool = new Pool(driverClassName, url, user, password, Config.getInt("POOL_SIZE"));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo inicializar el pool de conexiones", e);
        }
    }


    public static PoolManager getInstance(String driverClassName, String url, String user, String password) {
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");

        String key = driverClassName + "|" + url + "|" + user;
        return INSTANCES.computeIfAbsent(key, _k -> new PoolManager(driverClassName, url, user, password));
    }

    /**
     * Mantiene compatibilidad con el código anterior: devuelve el primer pool creado.
     */
    public static synchronized PoolManager getInstance() {
        return INSTANCES.values().stream().findFirst().orElseGet(() -> {
            throw new IllegalStateException("PoolManager no ha sido inicializado. Usa getInstance(driver,url,user,password) primero.");
        });
    }

    public Connection getConnection() {
        try {
            return pool.getConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void releaseConnection(Connection connection) {
        pool.releaseConnection(connection);
    }

    public void close() throws Exception {
        if (pool instanceof Pool p) {
            p.closePool();
        }
        INSTANCES.remove(instanceKey, this);
    }
}
