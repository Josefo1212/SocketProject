package dbcomponent;

import java.sql.SQLException;

public class DBException extends Exception {

    public enum Category {
        CONNECTION,
        AUTH,
        TIMEOUT,
        SYNTAX,
        CONSTRAINT,
        TRANSACTION,
        IO,
        CONFIG,
        UNKNOWN
    }

    private final Category category;

    private final String errorCode;

    // Metadata opcional (útil para diagnóstico)
    private final String sqlState;
    private final Integer vendorCode;
    private final String context;

    public DBException(String message) {
        this(Category.UNKNOWN, null, message, null, null, null, null);
    }

    public DBException(String message, Throwable cause) {
        this(Category.UNKNOWN, null, message, cause, null, null, null);
    }

    public DBException(Category category, String errorCode, String message) {
        this(category, errorCode, message, null, null, null, null);
    }

    public DBException(Category category, String errorCode, String message, Throwable cause) {
        this(category, errorCode, message, cause, null, null, null);
    }

    public DBException(Category category,
                       String errorCode,
                       String message,
                       Throwable cause,
                       String sqlState,
                       Integer vendorCode,
                       String context) {
        super(message, cause);
        this.category = category == null ? Category.UNKNOWN : category;
        this.errorCode = errorCode;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
        this.context = context;
    }

    public Category getCategory() {
        return category;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSqlState() {
        return sqlState;
    }

    public Integer getVendorCode() {
        return vendorCode;
    }

    public String getContext() {
        return context;
    }

    /**
     * Construye una DBException a partir de una SQLException, intentando inferir la categoría.
     */
    public static DBException fromSQLException(SQLException e, String contextMessage) {
        if (e == null) return new DBException(Category.UNKNOWN, null, contextMessage);

        String sqlState = e.getSQLState();
        Integer vendor = e.getErrorCode();

        Category cat = Category.UNKNOWN;
        if (sqlState != null && sqlState.length() >= 2) {
            String cls = sqlState.substring(0, 2);
            // Clases comunes (PostgreSQL / estándar SQLSTATE)
            switch (cls) {
                case "08" -> cat = Category.CONNECTION;   // connection exception
                case "28" -> cat = Category.AUTH;         // invalid authorization specification
                case "40" -> cat = Category.TRANSACTION;  // transaction rollback
                case "42" -> cat = Category.SYNTAX;       // syntax error or access rule violation
                case "23" -> cat = Category.CONSTRAINT;   // integrity constraint violation
                default -> cat = Category.UNKNOWN;
            }
        }

        String msg = contextMessage == null || contextMessage.isBlank()
                ? e.getMessage()
                : (contextMessage + ": " + e.getMessage());

        return new DBException(cat, sqlState, msg, e, sqlState, vendor, contextMessage);
    }
}
