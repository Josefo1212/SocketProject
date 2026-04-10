package DB.adapters;

import DB.adapters.IDBAdapter;
import DB.adapters.DatabaseType;
import DB.adapters.postgres.PostgreSQLAdapter;

public final class DBAdapterFactory {
    private DBAdapterFactory() {
    }

    public static IDBAdapter adapter(DatabaseType type) {
        if (type == null) throw new IllegalArgumentException("DatabaseType no puede ser null");
        return switch (type) {
            case POSTGRES -> new PostgreSQLAdapter();
        };
    }
}

