package pool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_POOL_TIMEOUT_MS = 3000L;
    private static final int DEFAULT_DB_PORT = 5432;
    private static final Properties properties = new Properties();

    static {
        try (var fis = new FileInputStream(".env")) {
            properties.load(fis);
        } catch (IOException e) {
            // `.env` es opcional: se usan variables de entorno o defaults.
        }
    }

    public static String get(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String fileValue = properties.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }
        return defaultString(key);
    }

    public static String getRequired(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta configurar la variable requerida: " + key);
        }
        return value;
    }

    public static int getInt(String key) {
        String raw = get(key);
        if (raw == null || raw.isBlank()) {
            return defaultInt(key);
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultInt(key);
        }
    }

    public static long getLong(String key) {
        String raw = get(key);
        if (raw == null || raw.isBlank()) {
            return defaultLong(key);
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultLong(key);
        }
    }

    private static int defaultInt(String key) {
        return switch (key) {
            case "POOL_SIZE" -> DEFAULT_POOL_SIZE;
            case "DB_PORT" -> DEFAULT_DB_PORT;
            default -> 0;
        };
    }

    private static long defaultLong(String key) {
        return "POOL_TIMEOUT".equals(key) ? DEFAULT_POOL_TIMEOUT_MS : 0L;
    }

    private static String defaultString(String key) {
        return switch (key) {
            case "DB_HOST" -> "127.0.0.1";
            case "DB_NAME" -> "SocketProject";
            case "DB_USER" -> "socket_user";
            case "DB_PASSWORD" -> "socket_pass_123";
            default -> null;
        };
    }
}

