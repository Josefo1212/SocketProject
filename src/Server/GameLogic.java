package Server;

import DB.dbcomponent.DBComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameLogic {
    private static final int MIN_PLAYERS_TO_START = 2;

    private final DBComponent db;
    private final GamePersistence persistence;
    private final GameState state = new GameState();
    private final List<OutgoingEvent> pendingEvents = new ArrayList<>();

    private final Map<Integer, Integer> userIdByClientId = new HashMap<>();
    private final Map<String, Integer> roomIdByCode = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> historyIdByRoomAndOwnerClient = new HashMap<>();

    public GameLogic(DBComponent db) {
        this.db = db;
        this.persistence = new GamePersistence(db);
    }

    public DBComponent getDb() {
        return db;
    }

    public synchronized String registerHello(int clientId, String rawName) {
        String name = state.registerPlayer(clientId, rawName);
        int userId = persistence.upsertUser(name);
        userIdByClientId.put(clientId, userId);
        return name;
    }

    public synchronized String createRoom(int clientId, String rawCode) {
        String roomCode = state.createRoom(clientId, rawCode);
        int roomId = persistence.upsertRoom(roomCode);
        roomIdByCode.put(roomCode, roomId);

        int userId = requireUserId(clientId);
        persistence.upsertParticipation(roomId, userId, 1, true, true);

        queueRoomInfo(roomCode, "LOBBY", "Sala creada. Esperando jugadores...");
        tryAutoStart(roomCode);
        return roomCode;
    }

    public synchronized String joinRoom(int clientId, String rawCode) {
        String roomCode = state.joinRoom(clientId, rawCode);
        int roomId = requireRoomId(roomCode);
        int userId = requireUserId(clientId);

        List<Integer> orderedPlayers = state.playersInRoomOrdered(roomCode);
        int order = orderedPlayers.indexOf(clientId) + 1;
        persistence.upsertParticipation(roomId, userId, order, false, true);

        int size = state.roomSize(roomCode);
        queueRoomInfo(roomCode, "LOBBY", "Jugador unido. Total=" + size);
        tryAutoStart(roomCode);
        return roomCode;
    }

    public synchronized String leaveRoom(int clientId) {
        String roomCode = state.roomCodeByClient(clientId);
        String playerName = state.playerName(clientId);
        String leftCode = state.leaveRoom(clientId);
        if (leftCode != null) {
            Integer roomId = roomIdByCode.get(leftCode);
            Integer userId = userIdByClientId.get(clientId);
            if (roomId != null && userId != null) {
                persistence.setParticipationActive(roomId, userId, false);
            }
            queueRoomInfo(leftCode, "LOBBY", (playerName == null ? "Jugador" : playerName) + " se desconecto");
        }
        return leftCode;
    }

    public synchronized RoomChat chat(int clientId, String rawMessage) {
        String roomCode = state.roomCodeByClient(clientId);
        if (roomCode == null) {
            throw new IllegalStateException("Debes unirte a una sala antes de usar CHAT");
        }

        return switch (state.roomPhase(roomCode)) {
            case LOBBY -> lobbyChat(clientId, roomCode, rawMessage);
            case IN_PROGRESS -> roundSubmission(clientId, rawMessage);
            case FINISHED -> new RoomChat(roomCode, "SYSTEM", "La partida ya termino en esta sala", Set.of(clientId));
        };
    }

    public synchronized String playerName(int clientId) {
        return state.playerName(clientId);
    }

    public synchronized int roomSize(String roomCode) {
        return state.roomSize(roomCode);
    }

    public synchronized void disconnect(int clientId) {
        String roomCode = state.roomCodeByClient(clientId);
        String playerName = state.playerName(clientId);

        String removedRoomCode = state.disconnect(clientId);
        Integer userId = userIdByClientId.remove(clientId);

        String targetRoomCode = removedRoomCode != null ? removedRoomCode : roomCode;
        if (targetRoomCode != null) {
            Integer roomId = roomIdByCode.get(targetRoomCode);
            if (roomId != null && userId != null) {
                persistence.setParticipationActive(roomId, userId, false);
            }

            String who = (playerName == null || playerName.isBlank()) ? "Un jugador" : playerName;
            queueEvent(
                    state.playersInRoom(targetRoomCode),
                    Map.of(
                            "type", "EVENT",
                            "event", "PLAYER_DISCONNECTED",
                            "roomCode", targetRoomCode,
                            "player", who,
                            "message", who + " se desconecto"
                    )
            );
        }
    }

    public synchronized List<OutgoingEvent> drainEvents() {
        List<OutgoingEvent> out = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return out;
    }

    private RoomChat lobbyChat(int clientId, String roomCode, String message) {
        String text = required(message);
        String playerName = state.playerName(clientId);
        Set<Integer> targets = new LinkedHashSet<>(state.playersInRoom(roomCode));
        return new RoomChat(roomCode, playerName, text, targets);
    }

    private RoomChat roundSubmission(int clientId, String text) {
        GameState.SubmissionResult result = state.submitRoundText(clientId, text);
        String playerName = state.playerName(clientId);

        int roomId = requireRoomId(result.roomCode());
        int roundId = persistence.ensureRound(roomId, result.submittedRound());

        int ownerClientId = result.targetOwnerClientId();
        int ownerUserId = requireUserId(ownerClientId);
        int authorUserId = requireUserId(clientId);
        int historyId = requireHistoryId(result.roomCode(), ownerClientId, ownerUserId);

        persistence.saveFragmentAndMarkAssignment(
                roundId,
                historyId,
                authorUserId,
                authorUserId,
                result.submittedRound(),
                required(text)
        );

        queueEvent(
                state.playersInRoom(result.roomCode()),
                Map.of(
                        "type", "EVENT",
                        "event", "ROUND_PROGRESS",
                        "roomCode", result.roomCode(),
                        "round", String.valueOf(result.submittedRound()),
                        "submitted", String.valueOf(result.submitted()),
                        "total", String.valueOf(result.total()),
                        "player", playerName == null ? "" : playerName
                )
        );

        if (result.roundCompleted()) {
            persistence.closeRound(roomId, result.submittedRound());

            if (result.gameFinished()) {
                persistence.finishGame(roomId);
                queueEvent(
                        state.playersInRoom(result.roomCode()),
                        Map.of(
                                "type", "EVENT",
                                "event", "GAME_FINISHED",
                                "roomCode", result.roomCode(),
                                "round", String.valueOf(result.submittedRound())
                        )
                );
                for (Map.Entry<Integer, String> e : result.finalStoryByOwner().entrySet()) {
                    queueEvent(
                            Set.of(e.getKey()),
                            Map.of(
                                    "type", "EVENT",
                                    "event", "STORY_RESULT",
                                    "roomCode", result.roomCode(),
                                    "story", e.getValue()
                            )
                    );
                }
            } else {
                int nextRoundId = persistence.ensureRound(roomId, result.activeRound());
                persistAssignments(result.roomCode(), nextRoundId, result.nextOwnerByAuthor());
                queueRoundStartEvents(result.roomCode(), result.activeRound(), result.maxRounds(), result.nextOwnerByAuthor());
            }
        }

        return new RoomChat(
                result.roomCode(),
                "SYSTEM",
                "Envio registrado en ronda " + result.submittedRound(),
                Set.of(clientId)
        );
    }

    private void tryAutoStart(String roomCode) {
        GameState.StartInfo startInfo = state.tryStartRoom(roomCode, MIN_PLAYERS_TO_START);
        if (startInfo == null) {
            return;
        }

        int roomId = requireRoomId(roomCode);
        persistence.markGameStarted(roomId);

        Map<Integer, Integer> storyByOwner = historyIdByRoomAndOwnerClient.computeIfAbsent(roomCode, _code -> new HashMap<>());
        for (int ownerClientId : state.playersInRoomOrdered(roomCode)) {
            int ownerUserId = requireUserId(ownerClientId);
            String ownerName = state.playerName(ownerClientId);
            int historyId = persistence.ensureStory(roomId, ownerUserId, "Historia de " + (ownerName == null ? "Jugador" : ownerName));
            storyByOwner.put(ownerClientId, historyId);
        }

        int roundId = persistence.ensureRound(roomId, startInfo.round());
        persistAssignments(roomCode, roundId, startInfo.ownerByAuthor());

        queueEvent(
                state.playersInRoom(roomCode),
                Map.of(
                        "type", "EVENT",
                        "event", "GAME_STARTED",
                        "roomCode", roomCode,
                        "round", String.valueOf(startInfo.round()),
                        "maxRounds", String.valueOf(startInfo.maxRounds())
                )
        );

        queueRoundStartEvents(roomCode, startInfo.round(), startInfo.maxRounds(), startInfo.ownerByAuthor());
    }

    private void persistAssignments(String roomCode, int roundId, Map<Integer, Integer> ownerByAuthor) {
        for (Map.Entry<Integer, Integer> e : ownerByAuthor.entrySet()) {
            int authorClientId = e.getKey();
            int ownerClientId = e.getValue();
            int ownerUserId = requireUserId(ownerClientId);
            int authorUserId = requireUserId(authorClientId);
            int historyId = requireHistoryId(roomCode, ownerClientId, ownerUserId);

            String preview = state.storyPreviewForOwner(roomCode, ownerClientId);
            persistence.upsertAssignment(roundId, historyId, authorUserId, preview);
        }
    }

    private void queueRoundStartEvents(String roomCode, int round, int maxRounds, Map<Integer, Integer> ownerByAuthor) {
        for (Map.Entry<Integer, Integer> e : ownerByAuthor.entrySet()) {
            String ownerName = state.playerName(e.getValue());
            queueEvent(
                    Set.of(e.getKey()),
                    Map.of(
                            "type", "EVENT",
                            "event", "ROUND_START",
                            "roomCode", roomCode,
                            "round", String.valueOf(round),
                            "maxRounds", String.valueOf(maxRounds),
                            "targetOwner", ownerName == null ? "" : ownerName
                    )
            );
        }
    }

    private void queueRoomInfo(String roomCode, String phase, String message) {
        queueEvent(
                state.playersInRoom(roomCode),
                Map.of(
                        "type", "EVENT",
                        "event", "ROOM_INFO",
                        "roomCode", roomCode,
                        "phase", phase,
                        "message", message
                )
        );
    }

    private void queueEvent(Set<Integer> targets, Map<String, String> payload) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        pendingEvents.add(new OutgoingEvent(Set.copyOf(targets), Map.copyOf(payload)));
    }

    private int requireUserId(int clientId) {
        Integer userId = userIdByClientId.get(clientId);
        if (userId == null) {
            throw new IllegalStateException("No existe usuario persistido para clientId=" + clientId);
        }
        return userId;
    }

    private int requireRoomId(String roomCode) {
        Integer roomId = roomIdByCode.get(roomCode);
        if (roomId == null) {
            throw new IllegalStateException("No existe sala persistida para code=" + roomCode);
        }
        return roomId;
    }

    private int requireHistoryId(String roomCode, int ownerClientId, int ownerUserId) {
        Map<Integer, Integer> storiesByOwner = historyIdByRoomAndOwnerClient.computeIfAbsent(roomCode, _code -> new HashMap<>());
        Integer historyId = storiesByOwner.get(ownerClientId);
        if (historyId != null) {
            return historyId;
        }

        int roomId = requireRoomId(roomCode);
        String ownerName = state.playerName(ownerClientId);
        int created = persistence.ensureStory(roomId, ownerUserId, "Historia de " + (ownerName == null ? "Jugador" : ownerName));
        storiesByOwner.put(ownerClientId, created);
        return created;
    }

    private String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Valor requerido vacio");
        }
        return value.trim();
    }

    public record RoomChat(String roomCode, String from, String message, Set<Integer> targetClientIds) {
    }

    public record OutgoingEvent(Set<Integer> targetClientIds, Map<String, String> payload) {
    }
}
