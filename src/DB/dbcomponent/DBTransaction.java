package dbcomponent;

/**
 * Contrato genérico para manejar transacciones.
 * La implementación concreta depende del motor (ej. PostgreSQL).
 */
public interface DBTransaction extends AutoCloseable {
    void begin() throws DBException;

    void commit() throws DBException;

    void rollback() throws DBException;

    /**
     * Indica si la transacción sigue activa (begin() ya fue llamado y aún no se hizo commit/rollback).
     */
    default boolean isActive() {
        return false;
    }

    @Override
    void close() throws DBException;
}
