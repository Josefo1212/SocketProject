package Server;

import common.JsonMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ClientHandler {
    private final Socket socket;
    private final int clientId;
    private final GameLogic gameLogic;
    private final BiConsumer<Integer, String> directSender;

    private final Object writeLock = new Object();
    private volatile BufferedWriter directWriter;

    public ClientHandler(Socket socket, int clientId, GameLogic gameLogic, BiConsumer<Integer, String> directSender) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.clientId = clientId;
        this.gameLogic = Objects.requireNonNull(gameLogic, "gameLogic");
        this.directSender = Objects.requireNonNull(directSender, "directSender");
    }

    public void handle() {
        String clientAddress = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("[Server] Cliente #" + clientId + " conectado desde " + clientAddress);

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        ) {
            directWriter = writer;
            send(writer, JsonMessage.mapOf("type", "INFO", "event", "CONNECTED", "clientId", String.valueOf(clientId)));
            flushPendingEvents();

            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, String> cmd = JsonMessage.parseObject(line);
                String type = upper(cmd.get("type"));
                if (type.isBlank()) {
                    sendError(writer, "BAD_REQUEST", "Mensaje JSON invalido o sin campo 'type'");
                    continue;
                }

                boolean keepRunning = handleCommand(cmd, type, writer);
                flushPendingEvents();
                if (!keepRunning) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Error con cliente #" + clientId + ": " + e.getMessage());
        } finally {
            String playerName = gameLogic.playerName(clientId);
            gameLogic.disconnect(clientId);
            flushPendingEvents();
            directWriter = null;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            String display = (playerName == null || playerName.isBlank()) ? ("#" + clientId) : (playerName + " (#" + clientId + ")");
            System.out.println("[Server] Cliente " + display + " desconectado.");
        }
    }

    private boolean handleCommand(Map<String, String> cmd, String type, BufferedWriter writer) throws IOException {
        try {
            return switch (type) {
                case "HELLO" -> {
                    String name = gameLogic.registerHello(clientId, cmd.get("playerName"));
                    send(writer, JsonMessage.mapOf("type", "OK", "event", "WELCOME", "playerName", name));
                    yield true;
                }
                case "CREATE_ROOM" -> {
                    String roomCode = gameLogic.createRoom(clientId, cmd.get("roomCode"));
                    send(writer, JsonMessage.mapOf("type", "OK", "event", "ROOM_CREATED", "roomCode", roomCode));
                    yield true;
                }
                case "JOIN_ROOM" -> {
                    String roomCode = gameLogic.joinRoom(clientId, cmd.get("roomCode"));
                    send(writer, JsonMessage.mapOf("type", "OK", "event", "ROOM_JOINED", "roomCode", roomCode));
                    yield true;
                }
                case "LEAVE_ROOM" -> {
                    String roomCode = gameLogic.leaveRoom(clientId);
                    send(writer, JsonMessage.mapOf("type", "OK", "event", "ROOM_LEFT", "roomCode", roomCode == null ? "" : roomCode));
                    yield true;
                }
                case "START_GAME" -> {
                    GameState.StartInfo info = gameLogic.startGame(clientId);
                    send(writer, JsonMessage.mapOf(
                            "type", "OK",
                            "event", "GAME_START_ACCEPTED",
                            "roomCode", info.roomCode(),
                            "round", String.valueOf(info.round()),
                            "maxRounds", String.valueOf(info.maxRounds())
                    ));
                    yield true;
                }
                case "SUBMIT_FRAGMENT" -> {
                    GameLogic.SubmissionContext result = gameLogic.submitFragment(clientId, cmd.get("text"));
                    send(writer, JsonMessage.mapOf(
                            "type", "OK",
                            "event", "FRAGMENT_ACCEPTED",
                            "roomCode", result.roomCode(),
                            "round", String.valueOf(result.round()),
                            "submitted", String.valueOf(result.submitted()),
                            "total", String.valueOf(result.total())
                    ));
                    yield true;
                }
                case "DISCONNECT_ALL" -> {
                    String actor = gameLogic.disconnectAllClients(clientId);
                    System.out.println("[Server] Solicitud DISCONNECT_ALL ejecutada por " + actor + " (#" + clientId + ")");
                    send(writer, JsonMessage.mapOf(
                            "type", "OK",
                            "event", "DISCONNECT_ALL_SENT",
                            "by", actor
                    ));
                    yield true;
                }
                case "PING" -> {
                    send(writer, JsonMessage.mapOf(
                            "type", "OK",
                            "event", "PONG",
                            "timestamp", String.valueOf(System.currentTimeMillis())
                    ));
                    yield true;
                }
                case "QUIT" -> {
                    System.out.println("[Server] Cliente #" + clientId + " envio QUIT");
                    send(writer, JsonMessage.mapOf("type", "OK", "event", "BYE", "message", "Hasta luego"));
                    yield false;
                }
                default -> {
                    sendError(writer, "UNKNOWN_COMMAND", type);
                    yield true;
                }
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(writer, "INVALID_STATE", e.getMessage());
            return true;
        }
    }

    private void flushPendingEvents() {
        for (GameLogic.OutgoingEvent event : gameLogic.drainEvents()) {
            String json = JsonMessage.stringify(event.payload());
            for (int targetClientId : event.targetClientIds()) {
                directSender.accept(targetClientId, json);
            }
        }
    }

    public void sendDirect(String jsonMessage) {
        BufferedWriter writer = directWriter;
        if (writer == null) {
            return;
        }

        synchronized (writeLock) {
            try {
                writer.write(jsonMessage);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private void send(BufferedWriter writer, Map<String, String> message) throws IOException {
        synchronized (writeLock) {
            writer.write(JsonMessage.stringify(message));
            writer.newLine();
            writer.flush();
        }
    }

    private void sendError(BufferedWriter writer, String code, String message) throws IOException {
        send(writer, JsonMessage.mapOf("type", "ERROR", "code", code, "message", message));
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
