package Server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GameState {
    public enum RoomPhase {
        LOBBY,
        IN_PROGRESS,
        FINISHED
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Integer, String> playersByClientId = new LinkedHashMap<>();
    private final Map<Integer, String> roomByClientId = new LinkedHashMap<>();
    private final Map<String, Room> roomsByCode = new LinkedHashMap<>();

    public String registerPlayer(int clientId, String playerName) {
        lock.writeLock().lock();
        try {
            String name = required(playerName);
            playersByClientId.put(clientId, name);
            return name;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String createRoom(int clientId, String roomCode) {
        lock.writeLock().lock();
        try {
            ensureIdentified(clientId);
            String code = normalizeRoomCode(roomCode);
            if (roomsByCode.containsKey(code)) {
                throw new IllegalArgumentException("La sala ya existe: " + code);
            }

            Room room = new Room(code, clientId);
            room.players.add(clientId);
            roomsByCode.put(code, room);
            roomByClientId.put(clientId, code);
            return code;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String joinRoom(int clientId, String roomCode) {
        lock.writeLock().lock();
        try {
            ensureIdentified(clientId);
            String code = normalizeRoomCode(roomCode);
            Room room = roomsByCode.get(code);
            if (room == null) {
                throw new IllegalArgumentException("No existe la sala: " + code);
            }
            if (room.phase != RoomPhase.LOBBY) {
                throw new IllegalStateException("La sala ya esta en partida");
            }

            leaveRoomInternal(clientId, true);
            room.players.add(clientId);
            roomByClientId.put(clientId, code);
            return code;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public StartInfo startGame(int clientId, int minPlayers) {
        lock.writeLock().lock();
        try {
            String roomCode = roomByClientId.get(clientId);
            if (roomCode == null) {
                throw new IllegalStateException("Debes unirte a una sala");
            }
            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                throw new IllegalStateException("La sala ya no existe");
            }
            if (room.hostClientId != clientId) {
                throw new IllegalStateException("Solo el host puede iniciar la partida");
            }
            if (room.phase != RoomPhase.LOBBY) {
                throw new IllegalStateException("La sala no esta en lobby");
            }
            if (room.players.size() < minPlayers) {
                throw new IllegalStateException("Se requieren al menos " + minPlayers + " jugadores");
            }

            room.phase = RoomPhase.IN_PROGRESS;
            room.round = 1;
            room.maxRounds = room.players.size();
            room.roundSubmissions.clear();
            room.storyByOwner.clear();
            for (int owner : room.players) {
                room.storyByOwner.put(owner, new ArrayList<>());
            }
            room.assignmentByAuthor = buildAssignment(room.players, room.round);

            return new StartInfo(room.code, room.round, room.maxRounds, Map.copyOf(room.assignmentByAuthor));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SubmissionResult submitFragment(int authorClientId, String rawText) {
        lock.writeLock().lock();
        try {
            return submitFragmentInternal(authorClientId, rawText);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SubmissionResult forceSubmit(int authorClientId, String fallbackText) {
        lock.writeLock().lock();
        try {
            return submitFragmentInternal(authorClientId, fallbackText);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<Integer> pendingAuthors(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null || room.phase != RoomPhase.IN_PROGRESS) {
                return Set.of();
            }
            LinkedHashSet<Integer> pending = new LinkedHashSet<>(room.players);
            pending.removeAll(room.roundSubmissions.keySet());
            return Set.copyOf(pending);
        } finally {
            lock.readLock().unlock();
        }
    }

    public RoomSnapshot roomSnapshot(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                return null;
            }
            String hostName = playersByClientId.getOrDefault(room.hostClientId, "Host");
            List<String> players = new ArrayList<>();
            for (int id : room.players) {
                players.add(playersByClientId.getOrDefault(id, "Jugador"));
            }
            return new RoomSnapshot(room.code, room.phase, hostName, players, room.round, room.maxRounds);
        } finally {
            lock.readLock().unlock();
        }
    }

    public DisconnectInfo disconnect(int clientId) {
        lock.writeLock().lock();
        try {
            String name = playersByClientId.remove(clientId);
            String roomCode = leaveRoomInternal(clientId, true);
            return new DisconnectInfo(clientId, name, roomCode);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String leaveRoom(int clientId) {
        lock.writeLock().lock();
        try {
            return leaveRoomInternal(clientId, false);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String roomCodeByClient(int clientId) {
        lock.readLock().lock();
        try {
            return roomByClientId.get(clientId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public String playerName(int clientId) {
        lock.readLock().lock();
        try {
            return playersByClientId.get(clientId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int roomSize(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            return room == null ? 0 : room.players.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Integer> playersInRoomOrdered(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                return List.of();
            }
            return List.copyOf(room.players);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Integer> playersInRoom(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                return Set.of();
            }
            return Set.copyOf(room.players);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Integer> allClientIds() {
        lock.readLock().lock();
        try {
            return Set.copyOf(playersByClientId.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public RoomPhase roomPhase(String roomCode) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            return room == null ? RoomPhase.LOBBY : room.phase;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String storyPreviewForOwner(String roomCode, int ownerClientId) {
        lock.readLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                return "";
            }
            List<RoundEntry> entries = room.storyByOwner.get(ownerClientId);
            if (entries == null || entries.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (RoundEntry entry : entries) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                sb.append(entry.text());
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    private SubmissionResult submitFragmentInternal(int authorClientId, String rawText) {
        ensureIdentified(authorClientId);
        String roomCode = roomByClientId.get(authorClientId);
        if (roomCode == null) {
            throw new IllegalStateException("Debes unirte a una sala");
        }
        Room room = roomsByCode.get(roomCode);
        if (room == null || room.phase != RoomPhase.IN_PROGRESS) {
            throw new IllegalStateException("La partida no esta en progreso");
        }
        if (!room.players.contains(authorClientId)) {
            throw new IllegalStateException("El jugador no pertenece a la sala");
        }
        if (room.roundSubmissions.containsKey(authorClientId)) {
            throw new IllegalStateException("Ya enviaste tu fragmento en esta ronda");
        }

        String text = required(rawText);
        int round = room.round;
        int ownerClientId = room.assignmentByAuthor.getOrDefault(authorClientId, authorClientId);

        room.roundSubmissions.put(authorClientId, text);
        room.storyByOwner.computeIfAbsent(ownerClientId, ignored -> new ArrayList<>())
                .add(new RoundEntry(round, authorClientId, text));

        int submitted = room.roundSubmissions.size();
        int total = room.players.size();
        boolean roundCompleted = submitted >= total;

        if (!roundCompleted) {
            return new SubmissionResult(
                    room.code,
                    round,
                    room.round,
                    room.maxRounds,
                    submitted,
                    total,
                    false,
                    false,
                    ownerClientId,
                    Map.of(),
                    Map.of(),
                    text
            );
        }

        room.roundSubmissions.clear();
        if (room.round >= room.maxRounds) {
            room.phase = RoomPhase.FINISHED;
            return new SubmissionResult(
                    room.code,
                    round,
                    room.round,
                    room.maxRounds,
                    submitted,
                    total,
                    true,
                    true,
                    ownerClientId,
                    Map.of(),
                    buildStorySummary(room),
                    text
            );
        }

        room.round++;
        room.assignmentByAuthor = buildAssignment(room.players, room.round);
        return new SubmissionResult(
                room.code,
                round,
                room.round,
                room.maxRounds,
                submitted,
                total,
                true,
                false,
                ownerClientId,
                Map.copyOf(room.assignmentByAuthor),
                Map.of(),
                text
        );
    }

    private String leaveRoomInternal(int clientId, boolean force) {
        String roomCode = roomByClientId.remove(clientId);
        if (roomCode == null) {
            return null;
        }

        Room room = roomsByCode.get(roomCode);
        if (room == null) {
            return roomCode;
        }

        if (!force && room.phase == RoomPhase.IN_PROGRESS) {
            roomByClientId.put(clientId, roomCode);
            throw new IllegalStateException("No puedes salir durante una partida");
        }

        room.players.remove(clientId);
        room.roundSubmissions.remove(clientId);
        if (room.hostClientId == clientId && !room.players.isEmpty()) {
            room.hostClientId = room.players.iterator().next();
        }

        if (room.players.isEmpty()) {
            roomsByCode.remove(roomCode);
        }
        return roomCode;
    }

    private Map<Integer, Integer> buildAssignment(Set<Integer> orderedPlayers, int round) {
        List<Integer> ordered = new ArrayList<>(orderedPlayers);
        int n = ordered.size();
        Map<Integer, Integer> assignment = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int author = ordered.get(i);
            int ownerIndex = Math.floorMod(i - (round - 1), n);
            assignment.put(author, ordered.get(ownerIndex));
        }
        return assignment;
    }

    private Map<Integer, String> buildStorySummary(Room room) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<RoundEntry>> entry : room.storyByOwner.entrySet()) {
            String ownerName = playersByClientId.getOrDefault(entry.getKey(), "Jugador");
            StringBuilder sb = new StringBuilder("Historia de ").append(ownerName).append(':');
            for (RoundEntry roundEntry : entry.getValue()) {
                String authorName = playersByClientId.getOrDefault(roundEntry.authorClientId(), "?");
                sb.append("\nR").append(roundEntry.round())
                        .append(" (").append(authorName).append("): ")
                        .append(roundEntry.text());
            }
            out.put(entry.getKey(), sb.toString());
        }
        return out;
    }

    private void ensureIdentified(int clientId) {
        if (!playersByClientId.containsKey(clientId)) {
            throw new IllegalStateException("Debes identificarte primero con HELLO");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Valor requerido vacio");
        }
        return value.trim();
    }

    private String normalizeRoomCode(String roomCode) {
        return required(roomCode).toUpperCase();
    }

    public record StartInfo(String roomCode, int round, int maxRounds, Map<Integer, Integer> ownerByAuthor) {
    }

    public record SubmissionResult(
            String roomCode,
            int submittedRound,
            int activeRound,
            int maxRounds,
            int submitted,
            int total,
            boolean roundCompleted,
            boolean gameFinished,
            int targetOwnerClientId,
            Map<Integer, Integer> nextOwnerByAuthor,
            Map<Integer, String> finalStoryByOwner,
            String submittedText
    ) {
    }

    public record RoomSnapshot(
            String roomCode,
            RoomPhase phase,
            String hostName,
            List<String> players,
            int round,
            int maxRounds
    ) {
    }

    public record DisconnectInfo(int clientId, String playerName, String roomCode) {
    }

    public record RoundEntry(int round, int authorClientId, String text) {
    }

    private static final class Room {
        private final String code;
        private int hostClientId;
        private final LinkedHashSet<Integer> players = new LinkedHashSet<>();
        private final Map<Integer, String> roundSubmissions = new LinkedHashMap<>();
        private final Map<Integer, List<RoundEntry>> storyByOwner = new LinkedHashMap<>();
        private Map<Integer, Integer> assignmentByAuthor = new LinkedHashMap<>();
        private RoomPhase phase = RoomPhase.LOBBY;
        private int round;
        private int maxRounds;

        private Room(String code, int hostClientId) {
            this.code = code;
            this.hostClientId = hostClientId;
        }
    }
}
