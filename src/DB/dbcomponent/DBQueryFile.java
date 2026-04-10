package dbcomponent;

import java.nio.file.Path;
import java.util.List;

/**
 * Contrato genérico para cargar/ejecutar queries desde archivos .sql.
 */
public interface DBQueryFile {
    DBQueryResult<?> queryFromFile(Path sqlFile) throws DBException;

    List<String> loadQueriesFromFile(Path sqlFile) throws DBException;
}

