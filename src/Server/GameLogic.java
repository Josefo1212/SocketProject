package Server;

import DB.dbcomponent.DBComponent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameLogic {
    private final DBComponent db;
    private final ConcurrentHashMap<Integer, String> playersByClientId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedHashSet<Integer>> membersByRoomCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> roomByClientId = new ConcurrentHashMap<>();

    public GameLogic(DBComponent db) {
        this.db = db;
    }

    public DBComponent getDb() {
        return db;
    }

    public synchronized String registerHello(int clientId, String rawName) {
        String name = normalizeRequired(rawName);
        playersByClientId.put(clientId, name);
        return name;
    }

    public synchronized String createRoom(int clientId, String rawCode) {
        ensureIdentified(clientId);
        String code = normalizeRequired(rawCode).toUpperCase();
        if (membersByRoomCode.containsKey(code)) {
            throw new IllegalArgumentException("La sala ya existe: " + code);
        }

        LinkedHashSet<Integer> members = new LinkedHashSet<>();
        members.add(clientId);
        membersByRoomCode.put(code, members);
        roomByClientId.put(clientId, code);
        return code;
    }

    public synchronized String joinRoom(int clientId, String rawCode) {
        ensureIdentified(clientId);
        String code = normalizeRequired(rawCode).toUpperCase();
        LinkedHashSet<Integer> members = membersByRoomCode.get(code);
        if (members == null) {
            throw new IllegalArgumentException("No existe la sala: " + code);
        }

        leaveRoomInternal(clientId);
        members.add(clientId);
        roomByClientId.put(clientId, code);
        return code;
    }

    public synchronized String leaveRoom(int clientId) {
        String previous = roomByClientId.get(clientId);
        leaveRoomInternal(clientId);
        return previous;
    }

    public synchronized RoomChat chat(int clientId, String rawMessage) {
        ensureIdentified(clientId);
        String roomCode = roomByClientId.get(clientId);
        if (roomCode == null) {
            throw new IllegalStateException("Debes unirte a una sala antes de usar CHAT");
        }

        String message = normalizeRequired(rawMessage);
        String playerName = playersByClientId.get(clientId);
        Set<Integer> targets = new LinkedHashSet<>(membersByRoomCode.getOrDefault(roomCode, new LinkedHashSet<>()));
        return new RoomChat(roomCode, playerName, message, targets);
    }

    public synchronized String playerName(int clientId) {
        return playersByClientId.get(clientId);
    }

    public synchronized int roomSize(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return 0;
        }
        LinkedHashSet<Integer> members = membersByRoomCode.get(roomCode.toUpperCase());
        return members == null ? 0 : members.size();
    }

    public synchronized void disconnect(int clientId) {
        leaveRoomInternal(clientId);
        playersByClientId.remove(clientId);
    }

    private void ensureIdentified(int clientId) {
        if (!playersByClientId.containsKey(clientId)) {
            throw new IllegalStateException("Primero debes enviar {\"type\":\"HELLO\",\"playerName\":\"...\"}");
        }
    }

    private void leaveRoomInternal(int clientId) {
        String roomCode = roomByClientId.remove(clientId);
        if (roomCode == null) {
            return;
        }

        LinkedHashSet<Integer> members = membersByRoomCode.get(roomCode);
        if (members == null) {
            return;
        }

        members.remove(clientId);
        if (members.isEmpty()) {
            membersByRoomCode.remove(roomCode);
        }
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Valor requerido vacio");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Valor requerido vacio");
        }
        return trimmed;
    }

    public record RoomChat(String roomCode, String from, String message, Set<Integer> targetClientIds) {
    }
}
