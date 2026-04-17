package Client;

import common.JsonMessage;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ClientUI {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color CARD_BG = new Color(18, 34, 56);
    private static final Color AREA_BG = new Color(10, 22, 38);
    private static final Color INPUT_BG = new Color(14, 28, 46);
    private static final Color BORDER_SOFT = new Color(255, 255, 255, 75);
    private static final Color TOP_INPUT_BG = new Color(250, 252, 255);
    private static final Color TOP_INPUT_FG = new Color(22, 38, 58);

    private final JFrame frame;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField playerField;
    private final JTextField roomCodeField;

    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JButton disconnectAllButton;
    private final JButton createRoomButton;
    private final JButton joinRoomButton;
    private final JButton startGameButton;
    private final JButton leaveRoomButton;
    private final JButton submitButton;

    private final JLabel roomLabel;
    private final JLabel phaseLabel;
    private final JLabel roundLabel;
    private final JLabel timerLabel;
    private final JLabel playersLabel;

    private final JTextArea promptArea;
    private final JTextArea fragmentInputArea;
    private final JTextArea resultsArea;
    private final JTextArea logArea;

    private volatile boolean connected;
    private String localPlayerName;
    private String currentHostName;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    private Timer countdownTimer;
    private int secondsRemaining;
    private volatile boolean closing;

    private ClientUI(String defaultPlayerName, int windowXOffset) {
        frame = new JFrame("SocketProject - Historia");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(980, 650);
        frame.setMinimumSize(new Dimension(880, 580));
        frame.setLocation(50 + windowXOffset, 60);

        hostField = new JTextField("127.0.0.1", 10);
        portField = new JTextField("5000", 5);
        playerField = new JTextField(defaultPlayerName, 12);
        roomCodeField = new JTextField(8);

        connectButton = new JButton("Conectar");
        disconnectButton = new JButton("Desconectar");
        disconnectAllButton = new JButton("Desconectar todos");
        createRoomButton = new JButton("Crear");
        joinRoomButton = new JButton("Unirse");
        startGameButton = new JButton("Comenzar");
        leaveRoomButton = new JButton("Salir sala");
        submitButton = new JButton("Enviar fragmento");

        roomLabel = statusLabel("Sala: --");
        phaseLabel = statusLabel("Fase: desconectado");
        roundLabel = statusLabel("Ronda: --");
        timerLabel = statusLabel("Tiempo: --");
        playersLabel = statusLabel("Jugadores: --");

        promptArea = createReadOnlyArea();
        fragmentInputArea = new JTextArea(4, 20);
        fragmentInputArea.setLineWrap(true);
        fragmentInputArea.setWrapStyleWord(true);
        fragmentInputArea.setFont(UiTheme.bodyFont());
        fragmentInputArea.setForeground(UiTheme.TEXT_PRIMARY);
        fragmentInputArea.setBackground(INPUT_BG);
        fragmentInputArea.setOpaque(true);
        fragmentInputArea.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SOFT, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        resultsArea = createReadOnlyArea();
        logArea = createReadOnlyArea();

        applyTheme();
        frame.setContentPane(buildRoot());

        connectButton.addActionListener(_e -> connect());
        disconnectButton.addActionListener(_e -> disconnect());
        disconnectAllButton.addActionListener(_e -> disconnectAllClients());
        createRoomButton.addActionListener(_e -> createRoom());
        joinRoomButton.addActionListener(_e -> joinRoom());
        leaveRoomButton.addActionListener(_e -> leaveRoom());
        startGameButton.addActionListener(_e -> startGame());
        submitButton.addActionListener(_e -> submitFragment());

        setConnectedState(false);
        appendLog("Cliente listo.");
    }

    public static void createAndShow(String defaultPlayerName, int windowXOffset) {
        ClientUI ui = new ClientUI(defaultPlayerName, windowXOffset);
        ui.frame.setVisible(true);
    }

    private JComponent buildRoot() {
        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel top = roundedPanel(new BorderLayout(8, 8));
        top.add(buildConnectionPanel(), BorderLayout.NORTH);
        top.add(buildRoomPanel(), BorderLayout.SOUTH);

        JPanel statusPanel = roundedPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        statusPanel.add(roomLabel);
        statusPanel.add(phaseLabel);
        statusPanel.add(roundLabel);
        statusPanel.add(timerLabel);
        statusPanel.add(playersLabel);

        JPanel gamePanel = roundedPanel(new BorderLayout(8, 8));
        gamePanel.setBorder(new CompoundBorder(gamePanel.getBorder(), new EmptyBorder(8, 8, 8, 8)));

        JLabel promptTitle = new JLabel("Turno de historia");
        promptTitle.setFont(UiTheme.subtitleFont());
        promptTitle.setForeground(UiTheme.TEXT_PRIMARY);

        promptArea.setText("Esperando inicio de ronda...");
        JScrollPane promptScroll = createScroll(promptArea, AREA_BG);

        JPanel submitPanel = new JPanel(new BorderLayout(8, 8));
        submitPanel.setOpaque(false);
        submitPanel.add(createScroll(fragmentInputArea, INPUT_BG), BorderLayout.CENTER);
        submitPanel.add(submitButton, BorderLayout.EAST);

        gamePanel.add(promptTitle, BorderLayout.NORTH);
        gamePanel.add(promptScroll, BorderLayout.CENTER);
        gamePanel.add(submitPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        rightPanel.setOpaque(false);

        JPanel resultsPanel = roundedPanel(new BorderLayout(6, 6));
        resultsPanel.add(sectionTitle("Historias finales"), BorderLayout.NORTH);
        resultsPanel.add(createScroll(resultsArea, AREA_BG), BorderLayout.CENTER);

        JPanel logPanel = roundedPanel(new BorderLayout(6, 6));
        logPanel.add(sectionTitle("Log"), BorderLayout.NORTH);
        logPanel.add(createScroll(logArea, AREA_BG), BorderLayout.CENTER);

        rightPanel.add(resultsPanel);
        rightPanel.add(logPanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gamePanel, rightPanel);
        split.setResizeWeight(0.58);
        split.setBorder(null);
        split.setOpaque(false);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setOpaque(false);
        content.add(statusPanel, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(configLabel("Host"));
        panel.add(hostField);
        panel.add(configLabel("Puerto"));
        panel.add(portField);
        panel.add(configLabel("Jugador"));
        panel.add(playerField);
        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(disconnectAllButton);
        return panel;
    }

    private JPanel buildRoomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(configLabel("Codigo"));
        panel.add(roomCodeField);
        panel.add(createRoomButton);
        panel.add(joinRoomButton);
        panel.add(startGameButton);
        panel.add(leaveRoomButton);
        return panel;
    }

    private JLabel configLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
        label.setForeground(UiTheme.TEXT_MUTED);
        return label;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.subtitleFont());
        label.setForeground(UiTheme.TEXT_PRIMARY);
        return label;
    }

    private JLabel statusLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
        label.setForeground(UiTheme.TEXT_PRIMARY);
        return label;
    }

    private JTextArea createReadOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UiTheme.bodyFont());
        area.setForeground(UiTheme.TEXT_PRIMARY);
        area.setBackground(AREA_BG);
        area.setOpaque(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        return area;
    }

    private JScrollPane createScroll(JComponent view, Color background) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(new LineBorder(BORDER_SOFT, 1, true));
        pane.setOpaque(true);
        pane.getViewport().setOpaque(true);
        pane.setBackground(background);
        pane.getViewport().setBackground(background);
        return pane;
    }

    private JPanel roundedPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(true);
        panel.setBackground(CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SOFT, 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        return panel;
    }

    private void applyTheme() {
        styleTopInput(hostField);
        styleTopInput(portField);
        styleTopInput(playerField);
        styleTopInput(roomCodeField);

        UiTheme.stylePrimaryButton(connectButton);
        UiTheme.styleSecondaryButton(disconnectButton);
        UiTheme.styleSecondaryButton(disconnectAllButton);
        UiTheme.stylePrimaryButton(createRoomButton);
        UiTheme.stylePrimaryButton(joinRoomButton);
        UiTheme.stylePrimaryButton(startGameButton);
        UiTheme.styleSecondaryButton(leaveRoomButton);
        UiTheme.stylePrimaryButton(submitButton);
    }

    private void styleTopInput(JTextField field) {
        field.setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
        field.setOpaque(true);
        field.setBackground(TOP_INPUT_BG);
        field.setForeground(TOP_INPUT_FG);
        field.setCaretColor(TOP_INPUT_FG);
        field.setBorder(new CompoundBorder(new LineBorder(new Color(132, 158, 186), 1, true), new EmptyBorder(7, 10, 7, 10)));
        field.setDisabledTextColor(new Color(84, 101, 121));
    }

    private void connect() {
        if (connected) {
            return;
        }

        String host = hostField.getText().trim();
        String playerName = playerField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            appendLog("Puerto invalido");
            return;
        }

        if (host.isBlank() || playerName.isBlank()) {
            appendLog("Host y jugador son obligatorios");
            return;
        }

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 2000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            connected = true;
            localPlayerName = playerName;
            startReader();
            sendJson(JsonMessage.mapOf("type", "HELLO", "playerName", playerName));
            setConnectedState(true);
            phaseLabel.setText("Fase: conectado");
            appendLog("Conectado como " + playerName);
        } catch (IOException e) {
            appendLog("No se pudo conectar: " + e.getMessage());
            closeSocketResources();
        }
    }

    private void disconnect() {
        disconnectInternal(true, "Desconectado");
    }

    private void disconnectAllClients() {
        sendCommand(JsonMessage.mapOf("type", "DISCONNECT_ALL"));
    }

    private void createRoom() {
        String code = roomCodeField.getText().trim().toUpperCase();
        if (code.isBlank()) {
            appendLog("Ingresa un codigo de sala");
            return;
        }
        sendCommand(JsonMessage.mapOf("type", "CREATE_ROOM", "roomCode", code));
    }

    private void joinRoom() {
        String code = roomCodeField.getText().trim().toUpperCase();
        if (code.isBlank()) {
            appendLog("Ingresa un codigo de sala");
            return;
        }
        sendCommand(JsonMessage.mapOf("type", "JOIN_ROOM", "roomCode", code));
    }

    private void leaveRoom() {
        sendCommand(JsonMessage.mapOf("type", "LEAVE_ROOM"));
    }

    private void startGame() {
        sendCommand(JsonMessage.mapOf("type", "START_GAME"));
    }

    private void submitFragment() {
        String text = fragmentInputArea.getText().trim();
        if (text.isBlank()) {
            appendLog("Escribe un fragmento antes de enviar");
            return;
        }
        sendCommand(JsonMessage.mapOf("type", "SUBMIT_FRAGMENT", "text", text));
        fragmentInputArea.setText("");
    }

    private void sendCommand(Map<String, String> payload) {
        if (!connected) {
            appendLog("Primero conecta el cliente");
            return;
        }
        try {
            sendJson(payload);
        } catch (IOException e) {
            appendLog("Error enviando comando: " + e.getMessage());
            disconnect();
        }
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    Map<String, String> message = JsonMessage.parseObject(line);
                    if (!message.isEmpty()) {
                        SwingUtilities.invokeLater(() -> handleIncoming(message));
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    appendLog("Conexion cerrada: " + e.getMessage());
                }
            } finally {
                if (connected) {
                    SwingUtilities.invokeLater(this::disconnect);
                }
            }
        }, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handleIncoming(Map<String, String> message) {
        String type = message.getOrDefault("type", "");
        String event = message.getOrDefault("event", "");

        if ("ERROR".equalsIgnoreCase(type)) {
            appendLog("Error: " + message.getOrDefault("message", "desconocido"));
            return;
        }

        if ("OK".equalsIgnoreCase(type)) {
            if ("DISCONNECT_ALL_SENT".equalsIgnoreCase(event)) {
                appendLog("Solicitud de desconexion global enviada");
            }
            return;
        }

        if (!"EVENT".equalsIgnoreCase(type)) {
            return;
        }

        switch (event) {
            case "ROOM_STATE" -> {
                roomLabel.setText("Sala: " + message.getOrDefault("roomCode", "--"));
                phaseLabel.setText("Fase: " + message.getOrDefault("phase", "--"));
                currentHostName = message.getOrDefault("host", "--");
                playersLabel.setText("Host: " + currentHostName + " | " + message.getOrDefault("players", "--"));
                startGameButton.setEnabled(connected && localPlayerName != null && localPlayerName.equalsIgnoreCase(currentHostName));
                String msg = message.getOrDefault("message", "");
                if (!msg.isBlank()) {
                    appendLog(msg);
                }
            }
            case "GAME_STARTED" -> {
                phaseLabel.setText("Fase: en partida");
                appendLog("Partida iniciada");
            }
            case "ROUND_STARTED" -> {
                String round = message.getOrDefault("round", "?");
                String maxRounds = message.getOrDefault("maxRounds", "?");
                roundLabel.setText("Ronda: " + round + "/" + maxRounds);
                String targetOwner = message.getOrDefault("targetOwner", "?");
                String prompt = message.getOrDefault("prompt", "");
                promptArea.setText("Continua la historia de: " + targetOwner + "\n\n" + prompt);
                startCountdown(parseIntSafe(message.get("seconds")));
                appendLog("Nueva ronda para historia de " + targetOwner);
            }
            case "ROUND_PROGRESS" -> appendLog("Avance: " + message.getOrDefault("submitted", "0") + "/" + message.getOrDefault("total", "0"));
            case "ROUND_TIMEOUT" -> appendLog("Se acabo el tiempo de la ronda");
            case "PLAYER_DISCONNECTED" -> appendLog(message.getOrDefault("message", "Un jugador se desconecto"));
            case "GAME_FINISHED" -> {
                stopCountdown();
                phaseLabel.setText("Fase: finalizada");
                appendLog("Partida finalizada");
            }
            case "STORY_RESULT" -> {
                String owner = message.getOrDefault("owner", "Jugador");
                if (!resultsArea.getText().isBlank()) {
                    resultsArea.append("\n\n");
                }
                resultsArea.append("=== " + owner + " ===\n" + message.getOrDefault("story", ""));
                resultsArea.setCaretPosition(resultsArea.getDocument().getLength());
            }
            case "FORCE_DISCONNECT" -> {
                appendLog(message.getOrDefault("message", "Desconexion global"));
                disconnectInternal(false, null);
            }
            default -> {
                // Silenciamos eventos tecnicos para que el cliente solo vea logs utiles.
            }
        }
    }

    private void startCountdown(int seconds) {
        stopCountdown();
        secondsRemaining = Math.max(1, seconds);
        timerLabel.setText("Tiempo: " + secondsRemaining + "s");

        countdownTimer = new Timer(1000, _e -> {
            secondsRemaining = Math.max(0, secondsRemaining - 1);
            timerLabel.setText("Tiempo: " + secondsRemaining + "s");
            if (secondsRemaining <= 0) {
                stopCountdown();
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void setConnectedState(boolean isConnected) {
        connectButton.setEnabled(!isConnected);
        disconnectButton.setEnabled(isConnected);
        disconnectAllButton.setEnabled(isConnected);
        createRoomButton.setEnabled(isConnected);
        joinRoomButton.setEnabled(isConnected);
        leaveRoomButton.setEnabled(isConnected);
        submitButton.setEnabled(isConnected);
        startGameButton.setEnabled(false);

        hostField.setEnabled(!isConnected);
        portField.setEnabled(!isConnected);
        playerField.setEnabled(!isConnected);
    }

    private void sendJson(Map<String, String> message) throws IOException {
        writer.write(JsonMessage.stringify(message));
        writer.newLine();
        writer.flush();
    }

    private void closeSocketResources() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        reader = null;
        writer = null;
        socket = null;
        readerThread = null;
    }

    private void disconnectInternal(boolean notifyServer, String logMessage) {
        if (closing) {
            return;
        }
        closing = true;
        connected = false;
        stopCountdown();
        setConnectedState(false);
        phaseLabel.setText("Fase: desconectado");
        roundLabel.setText("Ronda: --");
        timerLabel.setText("Tiempo: --");
        roomLabel.setText("Sala: --");
        playersLabel.setText("Jugadores: --");
        if (logMessage != null && !logMessage.isBlank()) {
            appendLog(logMessage);
        }

        Thread closer = new Thread(() -> {
            if (notifyServer) {
                try {
                    if (writer != null) {
                        sendJson(JsonMessage.mapOf("type", "QUIT"));
                    }
                } catch (IOException ignored) {
                }
            }
            closeSocketResources();
            closing = false;
        }, "client-socket-closer");
        closer.setDaemon(true);
        closer.start();
    }

    private int parseIntSafe(String value) {
        if (value == null) {
            return 45;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 45;
        }
    }

    private void appendLog(String line) {
        logArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + line + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static final class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, UiTheme.BG_TOP, 0, getHeight(), UiTheme.BG_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }
}
