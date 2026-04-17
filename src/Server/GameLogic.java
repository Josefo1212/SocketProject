package Server;

import DB.dbcomponent.DBComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameLogic {
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int ROUND_TIMEOUT_SECONDS = 45;
    private static final String TIMEOUT_TEXT = "[Sin respuesta]";

    private final DBComponent db;
    private final GamePersistence persistence;
    private final GameState state = new GameState();
    private final List<OutgoingEvent> pendingEvents = new ArrayList<>();

    private final Map<Integer, Integer> userIdByClientId = new HashMap<>();
    private final Map<String, Integer> roomIdByCode = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> historyIdByRoomAndOwnerClient = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> timeoutByRoom = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public GameLogic(DBComponent db) {
        this.db = db;
        this.persistence = new GamePersistence(db);
    }

    public DBComponent getDb() {
        return db;
    }

    public synchronized String registerHello(int clientId, String rawName) {
        String name = state.registerPlayer(clientId, rawName);
        userIdByClientId.put(clientId, persistence.upsertUser(name));
        return name;
    }

    public synchronized String createRoom(int clientId, String rawCode) {
        String roomCode = state.createRoom(clientId, rawCode);
        int roomId = persistence.upsertRoom(roomCode);
        roomIdByCode.put(roomCode, roomId);

        int userId = requireUserId(clientId);
        persistence.upsertParticipation(roomId, userId, 1, true, true);

        emitRoomState(roomCode, "Sala creada. Esperando jugadores...");
        return roomCode;
    }

    public synchronized String joinRoom(int clientId, String rawCode) {
        String roomCode = state.joinRoom(clientId, rawCode);
        int roomId = requireRoomId(roomCode);
        int userId = requireUserId(clientId);

        List<Integer> orderedPlayers = state.playersInRoomOrdered(roomCode);
        int order = orderedPlayers.indexOf(clientId) + 1;
        persistence.upsertParticipation(roomId, userId, order, false, true);

        emitRoomState(roomCode, "Jugador unido a la sala");
        return roomCode;
    }

    public synchronized String leaveRoom(int clientId) {
        String roomCode = state.leaveRoom(clientId);
        if (roomCode != null) {
            Integer roomId = roomIdByCode.get(roomCode);
            Integer userId = userIdByClientId.get(clientId);
            if (roomId != null && userId != null) {
                persistence.setParticipationActive(roomId, userId, false);
            }
            emitRoomState(roomCode, "Un jugador salio de la sala");
        }
        return roomCode;
    }

    public synchronized GameState.StartInfo startGame(int clientId) {
        GameState.StartInfo startInfo = state.startGame(clientId, MIN_PLAYERS_TO_START);
        String roomCode = startInfo.roomCode();

        int roomId = requireRoomId(roomCode);
        persistence.markGameStarted(roomId);

        Map<Integer, Integer> storiesByOwner = historyIdByRoomAndOwnerClient.computeIfAbsent(roomCode, ignored -> new HashMap<>());
        for (int ownerClientId : state.playersInRoomOrdered(roomCode)) {
            int ownerUserId = requireUserId(ownerClientId);
            String ownerName = state.playerName(ownerClientId);
            int historyId = persistence.ensureStory(roomId, ownerUserId, "Historia de " + (ownerName == null ? "Jugador" : ownerName));
            storiesByOwner.put(ownerClientId, historyId);
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

        emitRoundStartEvents(roomCode, startInfo.round(), startInfo.maxRounds(), startInfo.ownerByAuthor());
        scheduleRoundTimeout(roomCode);
        return startInfo;
    }

    public synchronized SubmissionContext submitFragment(int clientId, String rawText) {
        GameState.SubmissionResult result = state.submitFragment(clientId, rawText);
        applyPersistedSubmission(clientId, result);
        processSubmissionOutcome(result, false);

        return new SubmissionContext(result.roomCode(), result.submittedRound(), result.submitted(), result.total());
    }

    public synchronized String playerName(int clientId) {
        return state.playerName(clientId);
    }

    public synchronized int roomSize(String roomCode) {
        return state.roomSize(roomCode);
    }

    public synchronized void disconnect(int clientId) {
        GameState.DisconnectInfo info = state.disconnect(clientId);
        Integer userId = userIdByClientId.remove(clientId);
        if (info.roomCode() == null) {
            return;
        }

        cancelRoundTimeout(info.roomCode());

        Integer roomId = roomIdByCode.get(info.roomCode());
        if (roomId != null && userId != null) {
            persistence.setParticipationActive(roomId, userId, false);
        }

        String who = (info.playerName() == null || info.playerName().isBlank()) ? "Un jugador" : info.playerName();
        queueEvent(
                state.playersInRoom(info.roomCode()),
                Map.of(
                        "type", "EVENT",
                        "event", "PLAYER_DISCONNECTED",
                        "roomCode", info.roomCode(),
                        "player", who,
                        "message", who + " se desconecto"
                )
        );

        emitRoomState(info.roomCode(), "Actualizacion de sala tras desconexion");
    }

    public synchronized String disconnectAllClients(int requesterClientId) {
        String actor = state.playerName(requesterClientId);
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException("Debes estar conectado para desconectar a todos");
        }

        for (String roomCode : timeoutByRoom.keySet()) {
            cancelRoundTimeout(roomCode);
        }

        queueEvent(
                state.allClientIds(),
                Map.of(
                        "type", "EVENT",
                        "event", "FORCE_DISCONNECT",
                        "message", "Desconexion global solicitada por " + actor
                )
        );
        return actor;
    }

    public synchronized List<OutgoingEvent> drainEvents() {
        List<OutgoingEvent> out = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return out;
    }

    private void processSubmissionOutcome(GameState.SubmissionResult result, boolean timeoutDriven) {
        queueEvent(
                state.playersInRoom(result.roomCode()),
                Map.of(
                        "type", "EVENT",
                        "event", "ROUND_PROGRESS",
                        "roomCode", result.roomCode(),
                        "round", String.valueOf(result.submittedRound()),
                        "submitted", String.valueOf(result.submitted()),
                        "total", String.valueOf(result.total())
                )
        );

        if (!result.roundCompleted()) {
            return;
        }

        cancelRoundTimeout(result.roomCode());

        int roomId = requireRoomId(result.roomCode());
        persistence.closeRound(roomId, result.submittedRound());

        if (timeoutDriven) {
            queueEvent(
                    state.playersInRoom(result.roomCode()),
                    Map.of(
                            "type", "EVENT",
                            "event", "ROUND_TIMEOUT",
                            "roomCode", result.roomCode(),
                            "round", String.valueOf(result.submittedRound())
                    )
            );
        }

        if (result.gameFinished()) {
            persistence.finishGame(roomId);
            queueEvent(
                    state.playersInRoom(result.roomCode()),
                    Map.of(
                            "type", "EVENT",
                            "event", "GAME_FINISHED",
                            "roomCode", result.roomCode()
                    )
            );

            for (Map.Entry<Integer, String> entry : result.finalStoryByOwner().entrySet()) {
                String ownerName = state.playerName(entry.getKey());
                queueEvent(
                        state.playersInRoom(result.roomCode()),
                        Map.of(
                                "type", "EVENT",
                                "event", "STORY_RESULT",
                                "roomCode", result.roomCode(),
                                "owner", ownerName == null ? "Jugador" : ownerName,
                                "story", entry.getValue()
                        )
                );
            }
            return;
        }

        int nextRoundId = persistence.ensureRound(roomId, result.activeRound());
        persistAssignments(result.roomCode(), nextRoundId, result.nextOwnerByAuthor());
        emitRoundStartEvents(result.roomCode(), result.activeRound(), result.maxRounds(), result.nextOwnerByAuthor());
        scheduleRoundTimeout(result.roomCode());
    }

    private void applyPersistedSubmission(int authorClientId, GameState.SubmissionResult result) {
        int roomId = requireRoomId(result.roomCode());
        int roundId = persistence.ensureRound(roomId, result.submittedRound());

        int ownerClientId = result.targetOwnerClientId();
        int ownerUserId = requireUserId(ownerClientId);
        int authorUserId = requireUserId(authorClientId);
        int historyId = requireHistoryId(result.roomCode(), ownerClientId, ownerUserId);

        persistence.saveFragmentAndMarkAssignment(
                roundId,
                historyId,
                authorUserId,
                authorUserId,
                result.submittedRound(),
                result.submittedText()
        );
    }

    private void emitRoundStartEvents(String roomCode, int round, int maxRounds, Map<Integer, Integer> ownerByAuthor) {
        for (Map.Entry<Integer, Integer> entry : ownerByAuthor.entrySet()) {
            int authorClientId = entry.getKey();
            int ownerClientId = entry.getValue();
            String ownerName = state.playerName(ownerClientId);
            String prompt = state.storyPreviewForOwner(roomCode, ownerClientId);

            queueEvent(
                    Set.of(authorClientId),
                    Map.of(
                            "type", "EVENT",
                            "event", "ROUND_STARTED",
                            "roomCode", roomCode,
                            "round", String.valueOf(round),
                            "maxRounds", String.valueOf(maxRounds),
                            "seconds", String.valueOf(ROUND_TIMEOUT_SECONDS),
                            "targetOwner", ownerName == null ? "" : ownerName,
                            "prompt", prompt.isBlank() ? "Inicia una nueva historia" : prompt
                    )
            );
        }
    }

    private void emitRoomState(String roomCode, String message) {
        GameState.RoomSnapshot snapshot = state.roomSnapshot(roomCode);
        if (snapshot == null) {
            return;
        }

        String players = String.join(", ", snapshot.players());
        queueEvent(
                state.playersInRoom(roomCode),
                Map.of(
                        "type", "EVENT",
                        "event", "ROOM_STATE",
                        "roomCode", roomCode,
                        "phase", snapshot.phase().name(),
                        "host", snapshot.hostName(),
                        "players", players,
                        "message", message
                )
        );
    }

    private void scheduleRoundTimeout(String roomCode) {
        cancelRoundTimeout(roomCode);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> onRoundTimeout(roomCode),
                ROUND_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        timeoutByRoom.put(roomCode, future);
    }

    private void cancelRoundTimeout(String roomCode) {
        ScheduledFuture<?> current = timeoutByRoom.remove(roomCode);
        if (current != null) {
            current.cancel(false);
        }
    }

    private void onRoundTimeout(String roomCode) {
        synchronized (this) {
            if (state.roomPhase(roomCode) != GameState.RoomPhase.IN_PROGRESS) {
                return;
            }

            boolean applied = false;
            for (int authorClientId : state.pendingAuthors(roomCode)) {
                GameState.SubmissionResult result = state.forceSubmit(authorClientId, TIMEOUT_TEXT);
                applyPersistedSubmission(authorClientId, result);
                processSubmissionOutcome(result, true);
                applied = true;
            }

            if (!applied) {
                return;
            }
        }
    }

    private void persistAssignments(String roomCode, int roundId, Map<Integer, Integer> ownerByAuthor) {
        for (Map.Entry<Integer, Integer> entry : ownerByAuthor.entrySet()) {
            int authorClientId = entry.getKey();
            int ownerClientId = entry.getValue();
            int ownerUserId = requireUserId(ownerClientId);
            int authorUserId = requireUserId(authorClientId);
            int historyId = requireHistoryId(roomCode, ownerClientId, ownerUserId);
            String preview = state.storyPreviewForOwner(roomCode, ownerClientId);
            persistence.upsertAssignment(roundId, historyId, authorUserId, preview);
        }
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
            throw new IllegalStateException("No existe usuario para clientId=" + clientId);
        }
        return userId;
    }

    private int requireRoomId(String roomCode) {
        Integer roomId = roomIdByCode.get(roomCode);
        if (roomId == null) {
            throw new IllegalStateException("No existe sala para code=" + roomCode);
        }
        return roomId;
    }

    private int requireHistoryId(String roomCode, int ownerClientId, int ownerUserId) {
        Map<Integer, Integer> byOwner = historyIdByRoomAndOwnerClient.computeIfAbsent(roomCode, ignored -> new HashMap<>());
        Integer historyId = byOwner.get(ownerClientId);
        if (historyId != null) {
            return historyId;
        }

        int roomId = requireRoomId(roomCode);
        String ownerName = state.playerName(ownerClientId);
        int created = persistence.ensureStory(roomId, ownerUserId, "Historia de " + (ownerName == null ? "Jugador" : ownerName));
        byOwner.put(ownerClientId, created);
        return created;
    }

    public record SubmissionContext(String roomCode, int round, int submitted, int total) {
    }

    public record OutgoingEvent(Set<Integer> targetClientIds, Map<String, String> payload) {
    }
}
