package DB.dbcomponent;

public interface DBConnection {
    void connect() throws DBException;

    void disconnect() throws DBException;

    boolean isConnected();
}
