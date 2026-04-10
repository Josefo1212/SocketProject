package DB.dbcomponent;

public class DBQueryResult<T> {
    private final T result;
    private final int affectedRows;

    public DBQueryResult(T result, int affectedRows) {
        this.result = result;
        this.affectedRows = affectedRows;
    }

    public T getResult() {
        return result;
    }

    public int getAffectedRows() {
        return affectedRows;
    }
}
