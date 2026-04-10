package pool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (var fis = new FileInputStream(".env")) {
            properties.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar el archivo .env", e);
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public static long getLong(String key) {
        return Long.parseLong(properties.getProperty(key));
    }
}

