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
            playersByClientId.put(clientId, required(playerName));
            return playersByClientId.get(clientId);
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

            Room room = new Room(code);
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
                throw new IllegalStateException("La sala ya no esta en lobby");
            }

            leaveRoomInternal(clientId, false);
            room.players.add(clientId);
            roomByClientId.put(clientId, code);
            return code;
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

    public String disconnect(int clientId) {
        lock.writeLock().lock();
        try {
            String roomCode = leaveRoomInternal(clientId, true);
            playersByClientId.remove(clientId);
            return roomCode;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int roomSize(String roomCode) {
        lock.readLock().lock();
        try {
            if (roomCode == null || roomCode.isBlank()) {
                return 0;
            }
            Room room = roomsByCode.get(roomCode.toUpperCase());
            return room == null ? 0 : room.players.size();
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

    public String roomCodeByClient(int clientId) {
        lock.readLock().lock();
        try {
            return roomByClientId.get(clientId);
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

    public StartInfo tryStartRoom(String roomCode, int minPlayers) {
        lock.writeLock().lock();
        try {
            Room room = roomsByCode.get(roomCode);
            if (room == null || room.phase != RoomPhase.LOBBY || room.players.size() < minPlayers) {
                return null;
            }

            room.phase = RoomPhase.IN_PROGRESS;
            room.round = 1;
            room.maxRounds = room.players.size();
            room.currentRoundSubmissions.clear();

            room.storyByOwner.clear();
            for (int ownerId : room.players) {
                room.storyByOwner.put(ownerId, new ArrayList<>());
            }

            Map<Integer, Integer> targets = assignmentByAuthor(room, room.round);
            return new StartInfo(room.code, room.round, room.maxRounds, targets);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SubmissionResult submitRoundText(int authorClientId, String text) {
        lock.writeLock().lock();
        try {
            ensureIdentified(authorClientId);
            String roomCode = roomByClientId.get(authorClientId);
            if (roomCode == null) {
                throw new IllegalStateException("Debes unirte a una sala antes de enviar texto");
            }

            Room room = roomsByCode.get(roomCode);
            if (room == null) {
                throw new IllegalStateException("La sala ya no existe");
            }
            if (room.phase != RoomPhase.IN_PROGRESS) {
                throw new IllegalStateException("La partida no esta en progreso");
            }
            if (room.currentRoundSubmissions.containsKey(authorClientId)) {
                throw new IllegalStateException("Ya enviaste tu texto en esta ronda");
            }

            String clean = required(text);
            int submittedRound = room.round;
            room.currentRoundSubmissions.put(authorClientId, clean);

            int ownerClientId = targetOwner(room, authorClientId, submittedRound);
            room.storyByOwner.computeIfAbsent(ownerClientId, ignored -> new ArrayList<>())
                    .add(new RoundEntry(submittedRound, authorClientId, clean));

            int submitted = room.currentRoundSubmissions.size();
            int total = room.players.size();
            boolean roundCompleted = submitted >= total;

            if (!roundCompleted) {
                return new SubmissionResult(
                        room.code,
                        submittedRound,
                        room.round,
                        room.maxRounds,
                        submitted,
                        total,
                        false,
                        false,
                        ownerClientId,
                        Map.of(),
                        Map.of()
                );
            }

            room.currentRoundSubmissions.clear();
            if (room.round >= room.maxRounds) {
                room.phase = RoomPhase.FINISHED;
                return new SubmissionResult(
                        room.code,
                        submittedRound,
                        room.round,
                        room.maxRounds,
                        submitted,
                        total,
                        true,
                        true,
                        ownerClientId,
                        Map.of(),
                        buildStorySummary(room)
                );
            }

            room.round++;
            Map<Integer, Integer> nextTargets = assignmentByAuthor(room, room.round);
            return new SubmissionResult(
                    room.code,
                    submittedRound,
                    room.round,
                    room.maxRounds,
                    submitted,
                    total,
                    true,
                    false,
                    ownerClientId,
                    nextTargets,
                    Map.of()
            );
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String leaveRoomInternal(int clientId, boolean allowInProgress) {
        String roomCode = roomByClientId.remove(clientId);
        if (roomCode == null) {
            return null;
        }

        Room room = roomsByCode.get(roomCode);
        if (room == null) {
            return roomCode;
        }

        if (!allowInProgress && room.phase == RoomPhase.IN_PROGRESS) {
            roomByClientId.put(clientId, roomCode);
            throw new IllegalStateException("No puedes salir de una sala con partida en progreso");
        }

        room.players.remove(clientId);
        room.currentRoundSubmissions.remove(clientId);
        room.storyByOwner.remove(clientId);

        if (room.players.isEmpty()) {
            roomsByCode.remove(roomCode);
        }
        return roomCode;
    }

    private Map<Integer, Integer> assignmentByAuthor(Room room, int round) {
        List<Integer> ordered = new ArrayList<>(room.players);
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        for (int authorId : ordered) {
            targets.put(authorId, targetOwner(room, authorId, round));
        }
        return targets;
    }

    private int targetOwner(Room room, int authorId, int round) {
        List<Integer> ordered = new ArrayList<>(room.players);
        int n = ordered.size();
        int authorIndex = ordered.indexOf(authorId);
        if (authorIndex < 0 || n == 0) {
            throw new IllegalStateException("Autor no pertenece a la sala");
        }

        int ownerIndex = Math.floorMod(authorIndex - (round - 1), n);
        return ordered.get(ownerIndex);
    }

    private Map<Integer, String> buildStorySummary(Room room) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<RoundEntry>> e : room.storyByOwner.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (RoundEntry entry : e.getValue()) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                String authorName = playersByClientId.getOrDefault(entry.authorClientId(), "?");
                sb.append("R").append(entry.round()).append("(").append(authorName).append("): ").append(entry.text());
            }
            out.put(e.getKey(), sb.toString());
        }
        return out;
    }

    private void ensureIdentified(int clientId) {
        if (!playersByClientId.containsKey(clientId)) {
            throw new IllegalStateException("Debes identificarte antes con HELLO");
        }
    }

    private String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Valor requerido vacio");
        }
        return value.trim();
    }

    private String normalizeRoomCode(String value) {
        return required(value).toUpperCase();
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
            Map<Integer, String> finalStoryByOwner
    ) {
    }

    public record RoundEntry(int round, int authorClientId, String text) {
    }

    private static final class Room {
        private final String code;
        private final LinkedHashSet<Integer> players = new LinkedHashSet<>();
        private final Map<Integer, String> currentRoundSubmissions = new LinkedHashMap<>();
        private final Map<Integer, List<RoundEntry>> storyByOwner = new LinkedHashMap<>();
        private RoomPhase phase = RoomPhase.LOBBY;
        private int round;
        private int maxRounds;

        private Room(String code) {
            this.code = code;
        }
    }
}
