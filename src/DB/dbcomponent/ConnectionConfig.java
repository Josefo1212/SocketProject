package DB.dbcomponent;

/**
 * Datos de conexión que se piden en la UI.
 */
public record ConnectionConfig(
        String driverClassName,
        String url,
        String user,
        String password
) {
}
