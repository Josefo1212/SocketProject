package DB.dbcomponent;

import DB.adapters.DatabaseType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro simple para mantener 1 DBComponent por tipo de BD.
 *
 * La UI conecta y guarda el componente aquí; luego la simulación lo consume.
 */
public final class DBComponentRegistry {
    private static final ConcurrentHashMap<DatabaseType, DBComponent> COMPONENTS = new ConcurrentHashMap<>();

    private DBComponentRegistry() {
    }

    public static void put(DatabaseType type, DBComponent component) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        if (component == null) throw new IllegalArgumentException("component no puede ser null");
        COMPONENTS.put(type, component);
    }

    public static void putReplacing(DatabaseType type, DBComponent component) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        if (component == null) throw new IllegalArgumentException("component no puede ser null");

        DBComponent previous = COMPONENTS.put(type, component);
        if (previous != null && previous != component) {
            try {
                previous.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public static DBComponent get(DatabaseType type) {
        return COMPONENTS.get(type);
    }

    public static void clear(DatabaseType type) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");

        DBComponent removed = COMPONENTS.remove(type);
        if (removed != null) {
            try {
                removed.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isConnected(DatabaseType type) {
        DBComponent c = COMPONENTS.get(type);
        return c != null && c.isConnected();
    }
}
