package DB.dbcomponent;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Contrato para mapear una fila de un ResultSet a un tipo T.
 */
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(ResultSet rs) throws SQLException;
}
