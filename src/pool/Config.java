package pool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_POOL_TIMEOUT_MS = 3000L;
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
        return properties.getProperty(key);
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
        return "POOL_SIZE".equals(key) ? DEFAULT_POOL_SIZE : 0;
    }

    private static long defaultLong(String key) {
        return "POOL_TIMEOUT".equals(key) ? DEFAULT_POOL_TIMEOUT_MS : 0L;
    }
}

