package dbcomponent;

public record DBQueryId(String value) {
    public DBQueryId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DBQueryId no puede ser null/vacío");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

