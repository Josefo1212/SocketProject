package Server;

import DB.dbcomponent.DBComponent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GamePersistence {
    private final String url;
    private final String user;
    private final String password;

    public GamePersistence(DBComponent db) {
        this.url = db.getUrl();
        this.user = db.getUser();
        this.password = db.getPassword();
    }

    public int upsertUser(String playerName) {
        String sql = """
                INSERT INTO usuario(nombre)
                VALUES (?)
                ON CONFLICT(nombre) DO UPDATE SET nombre = EXCLUDED.nombre
                RETURNING id_usuario
                """;
        return queryInt(sql, playerName);
    }

    public int upsertRoom(String roomCode) {
        String sql = """
                INSERT INTO sala(codigo, estado)
                VALUES (?, 'esperando')
                ON CONFLICT(codigo) DO UPDATE SET codigo = EXCLUDED.codigo
                RETURNING id_sala
                """;
        return queryInt(sql, roomCode);
    }

    public void markGameStarted(int roomId) {
        String sql = "UPDATE sala SET estado = 'en_juego' WHERE id_sala = ?";
        execute(sql, roomId);
    }

    public void upsertParticipation(int roomId, int userId, int order, boolean host, boolean active) {
        String sql = """
                INSERT INTO participacion(id_usuario, id_sala, es_host, orden_turno, activo)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id_usuario, id_sala)
                DO UPDATE SET activo = EXCLUDED.activo
                """;
        execute(sql, userId, roomId, host, order, active);
    }

    public void setParticipationActive(int roomId, int userId, boolean active) {
        String sql = "UPDATE participacion SET activo = ? WHERE id_sala = ? AND id_usuario = ?";
        execute(sql, active, roomId, userId);
    }

    public int ensureStory(int roomId, int ownerUserId, String title) {
        String sql = """
                INSERT INTO historia(id_sala, id_jugador_creador, titulo, estado)
                VALUES (?, ?, ?, 'activa')
                ON CONFLICT(id_sala, id_jugador_creador)
                DO UPDATE SET estado = 'activa'
                RETURNING id_historia
                """;
        return queryInt(sql, roomId, ownerUserId, title);
    }

    public int ensureRound(int roomId, int roundNumber) {
        String sql = """
                INSERT INTO ronda(id_sala, numero_ronda, estado)
                VALUES (?, ?, 'en_progreso')
                ON CONFLICT(id_sala, numero_ronda)
                DO UPDATE SET estado = 'en_progreso', fecha_fin = NULL
                RETURNING id_ronda
                """;
        return queryInt(sql, roomId, roundNumber);
    }

    public void upsertAssignment(int roundId, int historyId, int assignedUserId, String previousText) {
        String sql = """
                INSERT INTO asignacion_ronda(id_ronda, id_historia, id_jugador_asignado, estado, texto_previo)
                VALUES (?, ?, ?, 'pendiente', ?)
                ON CONFLICT(id_ronda, id_jugador_asignado)
                DO UPDATE SET
                    id_historia = EXCLUDED.id_historia,
                    estado = 'pendiente',
                    texto_previo = EXCLUDED.texto_previo,
                    tiempo_respuesta = NULL
                """;
        execute(sql, roundId, historyId, assignedUserId, previousText == null ? "" : previousText);
    }

    public void saveFragmentAndMarkAssignment(
            int roundId,
            int historyId,
            int authorUserId,
            int assignedUserId,
            int order,
            String text
    ) {
        String fragmentSql = """
                INSERT INTO fragmento_historia(id_historia, id_ronda, id_jugador, contenido, orden)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id_historia, orden)
                DO UPDATE SET
                    id_jugador = EXCLUDED.id_jugador,
                    contenido = EXCLUDED.contenido,
                    fecha_envio = CURRENT_TIMESTAMP
                """;

        String assignmentSql = """
                UPDATE asignacion_ronda
                SET estado = 'respondida', tiempo_respuesta = CURRENT_TIMESTAMP
                WHERE id_ronda = ? AND id_historia = ? AND id_jugador_asignado = ?
                """;

        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(fragmentSql)) {
                ps.setInt(1, historyId);
                ps.setInt(2, roundId);
                ps.setInt(3, authorUserId);
                ps.setString(4, text);
                ps.setInt(5, order);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(assignmentSql)) {
                ps.setInt(1, roundId);
                ps.setInt(2, historyId);
                ps.setInt(3, assignedUserId);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo guardar fragmento/asignacion", e);
        }
    }

    public void closeRound(int roomId, int roundNumber) {
        String sql = """
                UPDATE ronda
                SET estado = 'completada', fecha_fin = CURRENT_TIMESTAMP
                WHERE id_sala = ? AND numero_ronda = ?
                """;
        execute(sql, roomId, roundNumber);
    }

    public void finishGame(int roomId) {
        String roomSql = "UPDATE sala SET estado = 'finalizada' WHERE id_sala = ?";
        String historySql = """
                UPDATE historia
                SET estado = 'completada', fecha_completada = CURRENT_TIMESTAMP
                WHERE id_sala = ?
                """;
        execute(roomSql, roomId);
        execute(historySql, roomId);
    }

    private int queryInt(String sql, Object... params) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new RuntimeException("No se obtuvo resultado para SQL: " + sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error ejecutando SQL", e);
        }
    }

    private void execute(String sql, Object... params) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error ejecutando SQL", e);
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}

