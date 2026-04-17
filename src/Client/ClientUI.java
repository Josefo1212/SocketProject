package Client;

import common.JsonMessage;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.AlphaComposite;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
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
    private static final boolean DEBUG_PROTOCOL = false;
    private static final int DEFAULT_ROUND_SECONDS = 60;
    private static final int URGENT_SECONDS_THRESHOLD = 10;
    private static final String COMMAND_PLACEHOLDER = "Escribe tu frase...";
    private static final String ROOM_CODE_PLACEHOLDER = "Código sala";

    private final JFrame frame;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField playerField;

    private final JTextPane gameLogPane;
    private final StyledDocument gameLogDoc;
    private final AttributeSet timestampAttrs;
    private final AttributeSet logAttrs;

    private final JTextArea promptArea;
    private final JTextArea storyArea;

    private final JTextField roomCodeField;
    private final JButton createRoomButton;
    private final JButton joinRoomButton;
    private final JButton leaveRoomButton;
    private final JButton pingButton;

    private final JTextField messageField;
    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JButton sendButton;
    private final JLabel typingIndicator;
    private final Timer typingBlinkTimer;

    private final RoundInfoPanel roundInfoPanel;
    private final ResponsiveMainPanel responsiveMainPanel;

    private JComponent topHeaderPanel;
    private JComponent configPanel;

    private volatile boolean connected;
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread socketReaderThread;

    private volatile String localPlayerName;

    private ClientUI(String defaultPlayerName, int windowXOffset) {
        frame = new JFrame("SocketProject - Cliente");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1300, 760);
        frame.setLocation(60 + windowXOffset, 70);
        frame.setMinimumSize(new Dimension(1100, 680));

        hostField = new JTextField("127.0.0.1");
        portField = new JTextField("5000");
        playerField = new JTextField(defaultPlayerName);

        connectButton = new JButton("Conectar");
        disconnectButton = new JButton("Desconectar");
        sendButton = new JButton("Enviar");

        UiTheme.styleInput(hostField);
        UiTheme.styleInput(portField);
        UiTheme.styleInput(playerField);
        UiTheme.stylePrimaryButton(connectButton);
        UiTheme.styleSecondaryButton(disconnectButton);
        UiTheme.stylePrimaryButton(sendButton);

        promptArea = createReadOnlyArea(UiTheme.bodyFont(), new Color(0x0A, 0x19, 0x2F));
        storyArea = createReadOnlyArea(UiTheme.infoFont(), new Color(230, 236, 245));

        gameLogPane = new JTextPane();
        gameLogPane.setEditable(false);
        gameLogPane.setFont(UiTheme.codeFont());
        gameLogPane.setForeground(UiTheme.CONSOLE_TEXT);
        gameLogPane.setBackground(UiTheme.CONSOLE_BG);
        gameLogPane.setOpaque(true);
        gameLogPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        gameLogDoc = gameLogPane.getStyledDocument();

        SimpleAttributeSet ts = new SimpleAttributeSet();
        StyleConstants.setForeground(ts, UiTheme.TIMESTAMP);
        timestampAttrs = ts;

        SimpleAttributeSet msg = new SimpleAttributeSet();
        StyleConstants.setForeground(msg, UiTheme.CONSOLE_TEXT);
        logAttrs = msg;

        messageField = new JTextField();
        UiTheme.styleCommandInput(messageField);
        installPlaceholder(messageField, COMMAND_PLACEHOLDER);

        roomCodeField = new JTextField();
        UiTheme.styleInput(roomCodeField);
        installLightPlaceholder(roomCodeField, ROOM_CODE_PLACEHOLDER);

        createRoomButton = new JButton("Crear sala");
        joinRoomButton = new JButton("Unirse");
        leaveRoomButton = new JButton("Salir");
        pingButton = new JButton("Ping");

        UiTheme.stylePrimaryButton(createRoomButton);
        UiTheme.stylePrimaryButton(joinRoomButton);
        UiTheme.styleSecondaryButton(leaveRoomButton);
        UiTheme.styleSecondaryButton(pingButton);

        typingIndicator = new JLabel("Tu turno...|");
        typingIndicator.setFont(UiTheme.subtitleFont());
        typingIndicator.setForeground(UiTheme.ORANGE);
        typingBlinkTimer = new Timer(530, _e -> {
            if (!typingIndicator.isVisible()) {
                return;
            }
            typingIndicator.setText(typingIndicator.getText().endsWith("|") ? "Tu turno... " : "Tu turno...|");
        });
        typingBlinkTimer.start();

        GradientPanel root = new GradientPanel();
        root.setLayout(new GridBagLayout());
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        GlassCardPanel card = new GlassCardPanel();
        card.setLayout(new BorderLayout(14, 14));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));
        topHeaderPanel = buildTopHeader();
        card.add(topHeaderPanel, BorderLayout.NORTH);

        roundInfoPanel = new RoundInfoPanel();
        responsiveMainPanel = buildMainContent();
        card.add(responsiveMainPanel, BorderLayout.CENTER);

        GridBagConstraints cardGbc = new GridBagConstraints();
        cardGbc.gridx = 0;
        cardGbc.gridy = 0;
        cardGbc.weightx = 1;
        cardGbc.weighty = 1;
        cardGbc.fill = GridBagConstraints.BOTH;
        root.add(card, cardGbc);

        frame.setContentPane(root);

        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                responsiveMainPanel.applyLayoutForWidth(frame.getWidth());
                roundInfoPanel.setCompact(frame.getWidth() < 900);
            }
        });

        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);

        connectButton.addActionListener(_e -> connect());
        disconnectButton.addActionListener(_e -> disconnect());
        sendButton.addActionListener(_e -> sendMessage());
        messageField.addActionListener(_e -> sendMessage());

        createRoomButton.addActionListener(_e -> createRoom());
        joinRoomButton.addActionListener(_e -> joinRoom());
        leaveRoomButton.addActionListener(_e -> leaveRoom());
        pingButton.addActionListener(_e -> ping());

        promptArea.setText("Escribe una frase creativa para continuar la historia.\nCuando llegue el evento de ronda, este panel mostrara la frase objetivo.");
        storyArea.setText("Historia acumulada:\n- Aun no hay fragmentos en esta ronda.");
        roundInfoPanel.setDisconnected();
        setRoomControlsEnabled(false);
    }

    public static void createAndShow(String defaultPlayerName, int windowXOffset) {
        ClientUI handler = new ClientUI(defaultPlayerName, windowXOffset);
        handler.frame.setVisible(true);
    }

    private JPanel buildTopHeader() {
        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.setOpaque(false);

        JLabel title = new JLabel("GARTIC PHONE - MODO HISTORIA");
        title.setFont(UiTheme.titleFont());
        title.setForeground(UiTheme.TEXT_PRIMARY);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        top.add(title, BorderLayout.NORTH);
        configPanel = buildConfigPanel();
        top.add(configPanel, BorderLayout.CENTER);
        return top;
    }

    private void forceHeaderRepaint() {
        // Importante: hay fondos translúcidos (glass/rounded). Un repaint parcial puede dejar residuos.
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::forceHeaderRepaint);
            return;
        }

        if (configPanel != null) {
            configPanel.revalidate();
            configPanel.repaint();
        }
        if (topHeaderPanel != null) {
            topHeaderPanel.revalidate();
            topHeaderPanel.repaint();
        }

        JRootPane root = frame.getRootPane();
        if (root != null) {
            root.revalidate();
            root.repaint();
        } else {
            frame.repaint();
        }
    }

    private JPanel buildConfigPanel() {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(), 40, new Color(255, 140, 66, 204));
        panel.setBorder(new CompoundBorder(new DottedBottomBorder(new Color(255, 179, 71), 3), new EmptyBorder(12, 16, 12, 16)));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        fields.add(configLabel("Host"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        fields.add(hostField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        fields.add(configLabel("Port"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.3;
        fields.add(portField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        fields.add(configLabel("User"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 0.6;
        fields.add(playerField, gbc);

        gbc.gridx = 6;
        gbc.weightx = 0;
        fields.add(connectButton, gbc);

        gbc.gridx = 7;
        fields.add(disconnectButton, gbc);

        panel.add(fields, BorderLayout.CENTER);

        return panel;
    }

    private ResponsiveMainPanel buildMainContent() {
        JPanel leftPanel = buildGamePanel();
        JPanel rightPanel = buildRoundAndChatPanel();
        ResponsiveMainPanel panel = new ResponsiveMainPanel(leftPanel, rightPanel);
        panel.applyLayoutForWidth(frame.getWidth());
        return panel;
    }

    private JPanel buildGamePanel() {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(10, 10), 24, UiTheme.PANEL_BG);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setOpaque(false);

        JLabel subtitle = new JLabel("ZONA DE JUEGO");
        subtitle.setFont(UiTheme.subtitleFont());
        subtitle.setForeground(UiTheme.TEXT_PRIMARY);
        top.add(subtitle, BorderLayout.NORTH);
        top.add(typingIndicator, BorderLayout.SOUTH);

        JPanel promptCard = new JPanel(new BorderLayout());
        promptCard.setOpaque(true);
        promptCard.setBackground(UiTheme.LIGHT_BLUE);
        promptCard.setBorder(new CompoundBorder(new LineBorder(UiTheme.ORANGE, 8, true), new EmptyBorder(20, 20, 20, 20)));
        promptArea.setFont(UiTheme.bodyFont().deriveFont(28f));
        promptArea.setForeground(UiTheme.TEXT_DARK);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setBorder(null);
        promptScroll.getViewport().setBackground(UiTheme.LIGHT_BLUE);
        promptScroll.getViewport().setOpaque(true);
        promptScroll.setOpaque(false);
        promptCard.add(promptScroll, BorderLayout.CENTER);

        JPanel storyCard = new JPanel(new BorderLayout());
        storyCard.setOpaque(true);
        storyCard.setBackground(new Color(20, 42, 68, 220));
        storyCard.setBorder(new CompoundBorder(new LineBorder(new Color(58, 123, 213, 180), 1, true), new EmptyBorder(10, 10, 10, 10)));

        JLabel storyTitle = new JLabel("Historia acumulada");
        storyTitle.setFont(UiTheme.subtitleFont());
        storyTitle.setForeground(UiTheme.TEXT_PRIMARY);
        storyCard.add(storyTitle, BorderLayout.NORTH);
        JScrollPane storyScroll = new JScrollPane(storyArea);
        storyScroll.setBorder(null);
        storyScroll.setOpaque(false);
        storyScroll.getViewport().setOpaque(false);
        storyCard.add(storyScroll, BorderLayout.CENTER);

        JSplitPane gameSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, promptCard, storyCard);
        gameSplit.setResizeWeight(0.5);
        gameSplit.setDividerSize(8);
        gameSplit.setBorder(null);
        gameSplit.setOpaque(false);

        // Input principal del jugador (más espacio y más cerca de la zona de juego)
        JPanel playInput = new JPanel(new BorderLayout(10, 0));
        playInput.setOpaque(false);
        playInput.setBorder(new EmptyBorder(10, 0, 0, 0));
        messageField.setPreferredSize(new Dimension(1, 48));
        playInput.add(messageField, BorderLayout.CENTER);
        playInput.add(sendButton, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(gameSplit, BorderLayout.CENTER);
        panel.add(playInput, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildRoundAndChatPanel() {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(10, 10), 24, UiTheme.PANEL_BG);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(roundInfoPanel, BorderLayout.CENTER);

        JPanel consoleCard = new JPanel(new BorderLayout());
        consoleCard.setOpaque(true);
        consoleCard.setBackground(UiTheme.CONSOLE_BG);
        consoleCard.setBorder(new CompoundBorder(new LineBorder(UiTheme.ORANGE, 2, true), new EmptyBorder(12, 12, 12, 12)));

        JLabel consoleTitle = new JLabel("Sala / Estado del juego");
        consoleTitle.setFont(UiTheme.subtitleFont());
        consoleTitle.setForeground(UiTheme.TEXT_PRIMARY);
        consoleTitle.setBorder(new EmptyBorder(0, 2, 10, 2));

        JPanel consoleHeader = new JPanel();
        consoleHeader.setOpaque(false);
        consoleHeader.setLayout(new BoxLayout(consoleHeader, BoxLayout.Y_AXIS));
        consoleHeader.add(consoleTitle);
        consoleHeader.add(buildRoomControls());
        consoleCard.add(consoleHeader, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(gameLogPane);
        logScroll.setBorder(null);
        logScroll.getViewport().setOpaque(true);
        logScroll.getViewport().setBackground(UiTheme.CONSOLE_BG);
        styleScrollBar(logScroll);

        consoleCard.add(logScroll, BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, consoleCard);
        rightSplit.setResizeWeight(0.36);
        rightSplit.setBorder(null);
        rightSplit.setOpaque(false);

        panel.add(rightSplit, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildRoomControls() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 10, 0));

        roomCodeField.setPreferredSize(new Dimension(160, 36));

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(roomCodeField, BorderLayout.CENTER);

        JPanel primaryButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        primaryButtons.setOpaque(false);
        primaryButtons.add(createRoomButton);
        primaryButtons.add(joinRoomButton);
        left.add(primaryButtons, BorderLayout.EAST);

        JPanel secondaryButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        secondaryButtons.setOpaque(false);
        secondaryButtons.add(leaveRoomButton);
        secondaryButtons.add(pingButton);

        root.add(left, BorderLayout.CENTER);
        root.add(secondaryButtons, BorderLayout.SOUTH);
        return root;
    }

    private JLabel configLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
        label.setForeground(UiTheme.TEXT_DARK);
        return label;
    }

    private JTextArea createReadOnlyArea(Font font, Color fg) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(font);
        area.setForeground(fg);
        area.setBorder(new EmptyBorder(6, 6, 6, 6));
        return area;
    }

    private void connect() {
        ClientConfig config;
        try {
            config = readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Configuracion invalida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(config.host, config.port), 2000);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            connected = true;
            startSocketReader();
            sendJson(JsonMessage.mapOf("type", "HELLO", "playerName", config.playerName));
        } catch (IOException ex) {
            appendLog("No se pudo conectar al servidor: " + ex.getMessage());
            safeCloseSocket();
            return;
        }

        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        sendButton.setEnabled(true);

        hostField.setEnabled(false);
        portField.setEnabled(false);
        playerField.setEnabled(false);

        clearTopFieldSelection();
        // Evita que quede caret/selección en campos deshabilitados (se ve como “bug”).
        messageField.requestFocusInWindow();

        setRoomControlsEnabled(true);

        localPlayerName = config.playerName;
        roundInfoPanel.setConnected(config.playerName);

        appendLog("Conectado -> " + config.host + ":" + config.port + " como " + config.playerName);

        forceHeaderRepaint();
    }

    private void disconnect() {
        connected = false;
        safeCloseSocket();

        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);

        hostField.setEnabled(true);
        portField.setEnabled(true);
        playerField.setEnabled(true);

        clearTopFieldSelection();
        hostField.requestFocusInWindow();

        setRoomControlsEnabled(false);

        appendLog("Desconectado.");
        roundInfoPanel.setDisconnected();

        forceHeaderRepaint();
    }

    private void clearTopFieldSelection() {
        try {
            hostField.select(0, 0);
            portField.select(0, 0);
            playerField.select(0, 0);
        } catch (RuntimeException ignored) {
            // no-op
        }
    }

    private void setRoomControlsEnabled(boolean enabled) {
        roomCodeField.setEnabled(enabled);
        createRoomButton.setEnabled(enabled);
        joinRoomButton.setEnabled(enabled);
        leaveRoomButton.setEnabled(enabled);
        pingButton.setEnabled(enabled);
    }

    private void createRoom() {
        String code = readRoomCode();
        if (code == null) return;
        sendQuick(JsonMessage.mapOf("type", "CREATE_ROOM", "roomCode", code), "Tú: creando sala " + code + "...");
    }

    private void joinRoom() {
        String code = readRoomCode();
        if (code == null) return;
        sendQuick(JsonMessage.mapOf("type", "JOIN_ROOM", "roomCode", code), "Tú: uniéndote a la sala " + code + "...");
    }

    private void leaveRoom() {
        sendQuick(JsonMessage.mapOf("type", "LEAVE_ROOM"), "Tú: saliendo de la sala...");
    }

    private void ping() {
        sendQuick(JsonMessage.mapOf("type", "PING"), "Tú: ping...");
    }

    private String readRoomCode() {
        if (!connected) {
            appendLog("Primero debes conectarte.");
            return null;
        }
        String raw = roomCodeField.getText() == null ? "" : roomCodeField.getText().trim();
        if (raw.isBlank() || ROOM_CODE_PLACEHOLDER.equals(raw)) {
            appendLog("Escribe un código de sala.");
            roomCodeField.requestFocusInWindow();
            return null;
        }
        return raw.toUpperCase();
    }

    private void sendQuick(Map<String, String> payload, String logLine) {
        if (!connected) {
            appendLog("Primero debes conectarte.");
            return;
        }
        try {
            sendJson(payload);
            appendLog(logLine);
        } catch (IOException ex) {
            appendLog("Error enviando comando: " + ex.getMessage());
            disconnect();
        }
    }

    private void sendMessage() {
        if (!connected) {
            appendLog("No puedes enviar: primero debes conectarte.");
            return;
        }

        String input = messageField.getText().trim();
        if (COMMAND_PLACEHOLDER.equals(input)) {
            return;
        }
        if (input.isEmpty()) {
            return;
        }

        Map<String, String> payload;
        try {
            payload = toJsonCommand(input);
        } catch (IllegalArgumentException ex) {
            appendLog("Comando invalido: " + ex.getMessage());
            return;
        }

        try {
            sendJson(payload);
            appendLog(formatOutgoingForHumans(input, payload));
            if ("CHAT".equalsIgnoreCase(payload.get("type"))) {
                roundInfoPanel.markSubmissionPending();
            }
        } catch (IOException ex) {
            appendLog("Error enviando mensaje: " + ex.getMessage());
            disconnect();
            return;
        }

        messageField.setText("");
    }

    private Map<String, String> toJsonCommand(String input) {
        if (!input.startsWith("/")) {
            return JsonMessage.mapOf("type", "CHAT", "message", input);
        }

        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        return switch (command) {
            case "/create" -> {
                if (arg.isBlank()) throw new IllegalArgumentException("usa /create CODIGO");
                yield JsonMessage.mapOf("type", "CREATE_ROOM", "roomCode", arg);
            }
            case "/join" -> {
                if (arg.isBlank()) throw new IllegalArgumentException("usa /join CODIGO");
                yield JsonMessage.mapOf("type", "JOIN_ROOM", "roomCode", arg);
            }
            case "/leave" -> JsonMessage.mapOf("type", "LEAVE_ROOM");
            case "/ping" -> JsonMessage.mapOf("type", "PING");
            case "/quit" -> JsonMessage.mapOf("type", "QUIT");
            default -> throw new IllegalArgumentException("comando no soportado");
        };
    }

    private void startSocketReader() {
        socketReaderThread = new Thread(() -> {
            try {
                String line;
                while (connected && reader != null && (line = reader.readLine()) != null) {
                    Map<String, String> message = JsonMessage.parseObject(line);
                    if (message.isEmpty()) {
                        if (DEBUG_PROTOCOL) {
                            appendLog("Server(raw): " + line);
                        }
                        continue;
                    }
                    SwingUtilities.invokeLater(() -> handleUiEvent(message));
                    String human = formatIncomingForHumans(message);
                    if (human != null && !human.isBlank()) {
                        appendLog(human);
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    appendLog("Conexion cerrada por el servidor: " + e.getMessage());
                }
            } finally {
                if (connected) {
                    SwingUtilities.invokeLater(this::disconnect);
                }
            }
        }, "client-socket-reader");
        socketReaderThread.setDaemon(true);
        socketReaderThread.start();
    }

    private String formatIncomingForHumans(Map<String, String> message) {
        if (DEBUG_PROTOCOL) {
            return "Server -> " + JsonMessage.stringify(message);
        }

        String type = message.getOrDefault("type", "");
        String event = message.getOrDefault("event", "");

        if ("ERROR".equalsIgnoreCase(type)) {
            String code = message.getOrDefault("code", "UNKNOWN");
            String msg = message.getOrDefault("message", "");
            return "Error del servidor (" + code + "): " + msg;
        }

        // Ruido de protocolo: no se muestra en la UI (solo útil para debug).
        if ("INFO".equalsIgnoreCase(type) && "CONNECTED".equalsIgnoreCase(event)) {
            return null;
        }

        if ("OK".equalsIgnoreCase(type) && "WELCOME".equalsIgnoreCase(event)) {
            return null;
        }

        if ("OK".equalsIgnoreCase(type) && "CHAT_SENT".equalsIgnoreCase(event)) {
            return null;
        }

        if ("OK".equalsIgnoreCase(type) && "ROOM_JOINED".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            String players = message.getOrDefault("players", "?");
            return "Sala " + room + ": unido/a. Jugadores en sala: " + players + ".";
        }

        if ("EVENT".equalsIgnoreCase(type) && "ROOM_INFO".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            String msg = message.getOrDefault("message", "");
            return "Sala " + room + ": " + msg;
        }

        if ("EVENT".equalsIgnoreCase(type) && "GAME_STARTED".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            String max = message.getOrDefault("maxRounds", "?");
            return "Sala " + room + ": partida iniciada (" + max + " rondas).";
        }

        if ("EVENT".equalsIgnoreCase(type) && "ROUND_START".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            String round = message.getOrDefault("round", "?");
            String max = message.getOrDefault("maxRounds", "?");
            String turn = message.getOrDefault("targetOwner", "--");
            return "Sala " + room + ": ronda " + round + "/" + max + ". Turno: " + turn + ".";
        }

        if ("EVENT".equalsIgnoreCase(type) && "ROUND_PROGRESS".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            String submitted = message.getOrDefault("submitted", "0");
            String total = message.getOrDefault("total", "?");
            String player = message.getOrDefault("player", "").trim();
            String suffix = player.isBlank() ? "" : (" (" + player + " listo/a)");
            return "Sala " + room + ": envíos " + submitted + "/" + total + suffix + ".";
        }

        if ("EVENT".equalsIgnoreCase(type) && "CHAT".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "?");
            String from = message.getOrDefault("from", "?");
            String msg = message.getOrDefault("message", "");
            return "[" + room + "] " + from + ": " + msg;
        }

        if ("EVENT".equalsIgnoreCase(type) && "STORY_RESULT".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            return "Sala " + room + ": historia final (ver panel Historia acumulada).";
        }

        if ("EVENT".equalsIgnoreCase(type) && "GAME_FINISHED".equalsIgnoreCase(event)) {
            String room = message.getOrDefault("roomCode", "--");
            return "Sala " + room + ": partida finalizada.";
        }

        // fallback: silencio (evita mensajes técnicos en la UI)
        return null;
    }

    private String formatOutgoingForHumans(String input, Map<String, String> payload) {
        if (DEBUG_PROTOCOL) {
            return "Tu -> " + JsonMessage.stringify(payload);
        }
        String type = payload.getOrDefault("type", "");
        if ("CHAT".equalsIgnoreCase(type)) {
            return "Tú: " + payload.getOrDefault("message", input);
        }
        return "Tú: " + input;
    }

    private void handleUiEvent(Map<String, String> message) {
        String type = message.getOrDefault("type", "");
        String event = message.getOrDefault("event", "");

        if (!"EVENT".equalsIgnoreCase(type)) {
            if ("OK".equalsIgnoreCase(type) && "CHAT_SENT".equalsIgnoreCase(event)) {
                roundInfoPanel.markSubmissionSent();
            }
            return;
        }

        switch (event.toUpperCase()) {
            case "ROOM_INFO" -> {
                String phase = message.getOrDefault("phase", "");
                String text = message.getOrDefault("message", "");
                roundInfoPanel.pushNotification(text);
                if ("LOBBY".equalsIgnoreCase(phase)) {
                    roundInfoPanel.setPhaseLobby();
                }
            }
            case "GAME_STARTED" -> {
                roundInfoPanel.pushNotification("¡Partida iniciada!");
            }
            case "ROUND_START" -> {
                int round = parseIntSafe(message.get("round"), 1);
                int max = parseIntSafe(message.get("maxRounds"), 1);
                String targetOwner = message.getOrDefault("targetOwner", "");
                roundInfoPanel.startRound(round, max, targetOwner);
            }
            case "ROUND_PROGRESS" -> {
                int round = parseIntSafe(message.get("round"), 1);
                int submitted = parseIntSafe(message.get("submitted"), 0);
                int total = parseIntSafe(message.get("total"), 0);
                String player = message.getOrDefault("player", "");
                roundInfoPanel.setRoundProgress(round, submitted, total);
                if (player != null && !player.isBlank()) {
                    roundInfoPanel.pushNotification("¡" + player + " completó la frase!");
                    if (localPlayerName != null && localPlayerName.equalsIgnoreCase(player)) {
                        roundInfoPanel.markSubmissionSent();
                    }
                }
            }
            case "PLAYER_DISCONNECTED" -> {
                String text = message.getOrDefault("message", "Jugador desconectado");
                roundInfoPanel.pushNotification(text);
            }
            case "GAME_FINISHED" -> {
                roundInfoPanel.finishGame();
                roundInfoPanel.pushNotification("Partida finalizada");
            }
            case "STORY_RESULT" -> {
                String room = message.getOrDefault("roomCode", "--");
                String story = message.getOrDefault("story", "");
                renderStory(room, story);
            }
            default -> {
                // No-op
            }
        }
    }

    private void renderStory(String roomCode, String rawStory) {
        String story = rawStory == null ? "" : rawStory.trim();
        if (story.isBlank()) {
            storyArea.setText("Historia acumulada (" + roomCode + "):\n- Sin historia.");
            return;
        }

        // El server suele mandar: "R1(...): ... | R2(...): ...". Lo mostramos en formato amigable.
        String[] parts = story.split("\\s*\\|\\s*");
        StringBuilder sb = new StringBuilder();
        sb.append("Historia acumulada (").append(roomCode).append("):\n");
        for (String part : parts) {
            String clean = part == null ? "" : part.trim();
            if (clean.isBlank()) {
                continue;
            }
            sb.append("- ").append(clean).append("\n");
        }
        storyArea.setText(sb.toString().trim());
        storyArea.setCaretPosition(0);
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void sendJson(Map<String, String> message) throws IOException {
        if (writer == null) {
            throw new IOException("socket no inicializado");
        }
        writer.write(JsonMessage.stringify(message));
        writer.newLine();
        writer.flush();
    }

    private void safeCloseSocket() {
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
        socketReaderThread = null;
    }

    private ClientConfig readConfig() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("El host no puede estar vacio.");
        }

        String playerName = playerField.getText().trim();
        if (playerName.isEmpty()) {
            throw new IllegalArgumentException("El nombre del jugador no puede estar vacio.");
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El puerto debe ser numerico.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }

        return new ClientConfig(host, port, playerName);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            try {
                String ts = "[" + LocalTime.now().format(TIME_FORMAT) + "] ";
                gameLogDoc.insertString(gameLogDoc.getLength(), ts, timestampAttrs);
                gameLogDoc.insertString(gameLogDoc.getLength(), line + System.lineSeparator(), logAttrs);
                gameLogPane.setCaretPosition(gameLogDoc.getLength());
            } catch (BadLocationException ignored) {
            }
        });
    }

    private static class ClientConfig {
        private final String host;
        private final int port;
        private final String playerName;

        private ClientConfig(String host, int port, String playerName) {
            this.host = host;
            this.port = port;
            this.playerName = playerName;
        }
    }

    private static final class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gradient = new GradientPaint(0, 0, UiTheme.BG_TOP, 0, getHeight(), UiTheme.BG_BOTTOM);
            g2.setPaint(gradient);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(255, 255, 255, 20));
            for (int y = 10; y < getHeight(); y += 18) {
                for (int x = 10; x < getWidth(); x += 18) {
                    g2.fillOval(x, y, 2, 2);
                }
            }

            g2.dispose();
        }
    }

    private static final class GlassCardPanel extends JPanel {
        private GlassCardPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Limpia el área completa para evitar ghosting en bordes redondeados.
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(AlphaComposite.SrcOver);

            int arc = 24;
            Shape shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, arc, arc);

            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRoundRect(8, 12, getWidth() - 8, getHeight() - 8, arc, arc);

            // Evita “ghosting” al repintar con transparencia: sobrescribe el buffer.
            g2.setComposite(AlphaComposite.Src);
            g2.setColor(UiTheme.GLASS_BG);
            g2.fill(shape);

            g2.setComposite(AlphaComposite.SrcOver);

            g2.setColor(UiTheme.GLASS_BORDER);
            g2.draw(shape);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;

        private RoundedPanel(LayoutManager layout, int arc, Color bg) {
            super(layout);
            this.arc = arc;
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Limpia el rect completo para que no queden residuos en las esquinas.
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, getWidth(), getHeight());
            // Ahora pinta el fondo redondeado sobrescribiendo.
            g2.setComposite(AlphaComposite.Src);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class DottedBottomBorder extends EmptyBorder {
        private final Color color;
        private final int stroke;

        private DottedBottomBorder(Color color, int stroke) {
            super(0, 0, stroke + 8, 0);
            this.color = color;
            this.stroke = stroke;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(color);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 8f}, 0f));
            int lineY = y + height - stroke - 1;
            g2.drawLine(x + 8, lineY, x + width - 8, lineY);
            g2.dispose();
        }
    }

    private static void styleScrollBar(JScrollPane scrollPane) {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        bar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        bar.setOpaque(false);
        bar.setUI(new HoverAccentScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new HoverAccentScrollBarUI());
    }

    private static final class HoverAccentScrollBarUI extends BasicScrollBarUI {
        private volatile boolean hovering;

        @Override
        protected void installListeners() {
            super.installListeners();
            scrollbar.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hovering = true;
                    scrollbar.repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hovering = false;
                    scrollbar.repaint();
                }
            });
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = new Color(0, 0, 0, 0);
            thumbColor = new Color(255, 140, 66, 120);
            thumbDarkShadowColor = new Color(0, 0, 0, 0);
            thumbHighlightColor = new Color(0, 0, 0, 0);
            thumbLightShadowColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int arc = 12;
            Color base = hovering ? new Color(255, 140, 66, 220) : new Color(255, 140, 66, 120);
            g2.setColor(base);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, Math.max(6, thumbBounds.width - 4), Math.max(18, thumbBounds.height - 4), arc, arc);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // track transparente
        }
    }

    private static void installPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(new Color(245, 245, 245, 170));
        field.setFont(field.getFont().deriveFont(Font.ITALIC));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (placeholder.equals(field.getText())) {
                    field.setText("");
                    field.setForeground(UiTheme.TEXT_PRIMARY);
                    field.setFont(field.getFont().deriveFont(Font.PLAIN));
                }
                field.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (field.getText() == null || field.getText().isBlank()) {
                    field.setText(placeholder);
                    field.setForeground(new Color(245, 245, 245, 170));
                    field.setFont(field.getFont().deriveFont(Font.ITALIC));
                }
                field.repaint();
            }
        });
    }

    private static void installLightPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(new Color(10, 25, 47, 140));
        field.setFont(field.getFont().deriveFont(Font.ITALIC));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (placeholder.equals(field.getText())) {
                    field.setText("");
                    field.setForeground(UiTheme.TEXT_DARK);
                    field.setFont(field.getFont().deriveFont(Font.PLAIN));
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (field.getText() == null || field.getText().isBlank()) {
                    field.setText(placeholder);
                    field.setForeground(new Color(10, 25, 47, 140));
                    field.setFont(field.getFont().deriveFont(Font.ITALIC));
                }
            }
        });
    }

    private final class ResponsiveMainPanel extends JPanel {
        private final JPanel left;
        private final JPanel right;
        private boolean layoutInitialized;
        private boolean singleColumnApplied;

        private ResponsiveMainPanel(JPanel left, JPanel right) {
            super(new GridBagLayout());
            this.left = left;
            this.right = right;
            setOpaque(false);
        }

        void applyLayoutForWidth(int width) {
            boolean singleColumn = width < 900;
            if (layoutInitialized && singleColumn == singleColumnApplied) {
                return;
            }
            layoutInitialized = true;
            singleColumnApplied = singleColumn;
            removeAll();

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 0, singleColumn ? 10 : 0, singleColumn ? 0 : 10);
            gbc.fill = GridBagConstraints.BOTH;

            if (singleColumn) {
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weightx = 1;
                gbc.weighty = 0.55;
                add(left, gbc);

                gbc.gridy = 1;
                gbc.weighty = 0.45;
                gbc.insets = new Insets(0, 0, 0, 0);
                add(right, gbc);
            } else {
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weightx = 0.60;
                gbc.weighty = 1;
                add(left, gbc);

                gbc.gridx = 1;
                gbc.weightx = 0.40;
                gbc.insets = new Insets(0, 0, 0, 0);
                add(right, gbc);
            }

            revalidate();
            repaint();
        }
    }

    private static final class GradientDivider extends JComponent {
        private final int height;

        private GradientDivider(int height) {
            this.height = height;
            setOpaque(false);
            setPreferredSize(new Dimension(1, height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint paint = new GradientPaint(0, 0, UiTheme.BRAND_BLUE, getWidth(), 0, UiTheme.ORANGE);
            g2.setPaint(paint);
            g2.fillRoundRect(0, (getHeight() - 3) / 2, getWidth(), 3, 6, 6);
            g2.dispose();
        }
    }

    private enum SubmitState {
        WAITING,
        SENDING,
        SENT
    }

    private static final class StatusIndicator extends JComponent {
        private SubmitState state = SubmitState.WAITING;
        private float spinnerAngle = 0f;
        private final Timer spinner;

        private StatusIndicator() {
            setOpaque(false);
            setPreferredSize(new Dimension(28, 28));
            spinner = new Timer(60, _e -> {
                spinnerAngle += 0.25f;
                repaint();
            });
        }

        void setState(SubmitState state) {
            this.state = state;
            if (state == SubmitState.SENDING) {
                if (!spinner.isRunning()) {
                    spinner.start();
                }
            } else {
                if (spinner.isRunning()) {
                    spinner.stop();
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight());
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;

            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillOval(x, y, s, s);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawOval(x, y, s - 1, s - 1);

            if (state == SubmitState.SENT) {
                g2.setFont(new Font("Poppins", Font.BOLD, 16));
                g2.setColor(new Color(170, 255, 210));
                FontMetrics fm = g2.getFontMetrics();
                String check = "✓";
                int tx = x + (s - fm.stringWidth(check)) / 2;
                int ty = y + (s + fm.getAscent()) / 2 - 2;
                g2.drawString(check, tx, ty);
            } else if (state == SubmitState.SENDING) {
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(UiTheme.ORANGE);
                int pad = 7;
                int arcSize = s - pad * 2;
                g2.drawArc(x + pad, y + pad, arcSize, arcSize, (int) (spinnerAngle * 180 / Math.PI), 260);
            } else {
                g2.setFont(new Font("Poppins", Font.BOLD, 16));
                g2.setColor(new Color(255, 255, 255, 170));
                FontMetrics fm = g2.getFontMetrics();
                String dots = "…";
                int tx = x + (s - fm.stringWidth(dots)) / 2;
                int ty = y + (s + fm.getAscent()) / 2 - 2;
                g2.drawString(dots, tx, ty);
            }

            g2.dispose();
        }
    }

    private static final class BadgeLabel extends JLabel {
        private BadgeLabel(String text) {
            super(text);
            setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
            setForeground(UiTheme.TEXT_PRIMARY);
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiTheme.ORANGE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class TimerCircle extends JComponent {
        private int totalSeconds = DEFAULT_ROUND_SECONDS;
        private int remainingSeconds = DEFAULT_ROUND_SECONDS;
        private boolean urgent;
        private boolean compact;
        private float clockAngle;
        private final Timer animation;

        private TimerCircle() {
            setOpaque(false);
            setPreferredSize(new Dimension(190, 190));
            animation = new Timer(40, _e -> {
                clockAngle += 0.06f;
                repaint();
            });
            animation.start();
        }

        void setCompact(boolean compact) {
            if (this.compact == compact) {
                return;
            }
            this.compact = compact;
            setPreferredSize(compact ? new Dimension(140, 140) : new Dimension(190, 190));
            revalidate();
            repaint();
        }

        void setTotalSeconds(int totalSeconds) {
            this.totalSeconds = Math.max(1, totalSeconds);
            repaint();
        }

        void setRemainingSeconds(int remainingSeconds) {
            this.remainingSeconds = Math.max(0, remainingSeconds);
            urgent = remainingSeconds > 0 && remainingSeconds <= URGENT_SECONDS_THRESHOLD;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight());
            int pad = compact ? 10 : 12;
            int s = size - pad * 2;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;

            float ratio = Math.max(0f, Math.min(1f, remainingSeconds / (float) totalSeconds));
            int startAngle = 90;
            int sweep = Math.round(-360f * ratio);

            // base ring
            g2.setStroke(new BasicStroke(compact ? 10f : 12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 25));
            g2.drawArc(x, y, s, s, 0, 360);

            // progress ring (blue -> orange when urgent)
            Color ring = urgent ? UiTheme.ORANGE : UiTheme.BRAND_BLUE;
            g2.setColor(ring);
            g2.drawArc(x, y, s, s, startAngle, sweep);

            // inner circle
            g2.setColor(new Color(15, 23, 42, 210));
            g2.fillOval(x + (compact ? 14 : 16), y + (compact ? 14 : 16), s - (compact ? 28 : 32), s - (compact ? 28 : 32));

            // animated clock
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = (s - (compact ? 50 : 64)) / 2;
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillOval(cx - 3, cy - 3, 6, 6);

            float minute = clockAngle;
            float hour = clockAngle * 0.5f;
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 210));
            g2.drawLine(cx, cy, cx + (int) (Math.cos(hour) * (r * 0.55)), cy + (int) (Math.sin(hour) * (r * 0.55)));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(urgent ? UiTheme.ORANGE : new Color(255, 255, 255, 170));
            g2.drawLine(cx, cy, cx + (int) (Math.cos(minute) * (r * 0.85)), cy + (int) (Math.sin(minute) * (r * 0.85)));

            // remaining seconds text
            String text = String.valueOf(remainingSeconds);
            g2.setFont(UiTheme.subtitleFont().deriveFont(compact ? 18f : 22f));
            FontMetrics fm = g2.getFontMetrics();
            int tx = cx - fm.stringWidth(text) / 2;
            int ty = cy + r + fm.getAscent() + (compact ? 2 : 6);
            g2.setColor(new Color(245, 245, 245, 220));
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }

    private static final class NotificationCarousel extends JComponent {
        private String current = "";
        private String next = "";
        private float offset = 0f;
        private boolean sliding;
        private final Timer animator;

        private NotificationCarousel() {
            setOpaque(false);
            setPreferredSize(new Dimension(1, 46));
            animator = new Timer(16, _e -> {
                if (!sliding) {
                    return;
                }
                offset += 18f;
                if (offset >= getWidth()) {
                    current = next;
                    next = "";
                    offset = 0f;
                    sliding = false;
                }
                repaint();
            });
            animator.start();
        }

        void push(String message) {
            if (message == null) {
                return;
            }
            String clean = message.trim();
            if (clean.isBlank()) {
                return;
            }
            if (current.isBlank()) {
                current = clean;
                repaint();
                return;
            }
            next = clean;
            offset = 0f;
            sliding = true;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(255, 255, 255, 16));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);

            g2.setFont(UiTheme.infoFont());
            g2.setColor(UiTheme.TEXT_PRIMARY);

            int padding = 12;
            int baseY = getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
            if (!sliding || next.isBlank()) {
                drawClippedString(g2, current, padding, baseY, getWidth() - padding * 2);
            } else {
                int dx = (int) offset;
                Graphics2D gA = (Graphics2D) g2.create();
                gA.translate(-dx, 0);
                drawClippedString(gA, current, padding, baseY, getWidth() - padding * 2);
                gA.dispose();

                Graphics2D gB = (Graphics2D) g2.create();
                gB.translate(getWidth() - dx, 0);
                drawClippedString(gB, next, padding, baseY, getWidth() - padding * 2);
                gB.dispose();
            }

            g2.dispose();
        }

        private void drawClippedString(Graphics2D g2, String text, int x, int y, int width) {
            Shape old = g2.getClip();
            g2.setClip(x, 0, width, getHeight());
            g2.drawString(text, x, y);
            g2.setClip(old);
        }
    }

    private final class RoundInfoPanel extends JPanel {
        private final TimerCircle timer;
        private final BadgeLabel turnBadge;
        private final JLabel phaseLabel;
        private final JLabel roundLabel;
        private final JLabel progressLabel;
        private final StatusIndicator submitStatus;
        private final NotificationCarousel carousel;

        private Timer countdown;
        private int remaining;
        private int total;
        private boolean compact;

        private RoundInfoPanel() {
            super();
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("Info de ronda");
            title.setFont(UiTheme.subtitleFont());
            title.setForeground(UiTheme.TEXT_PRIMARY);
            add(title);
            add(Box.createVerticalStrut(8));
            add(new GradientDivider(8));
            add(Box.createVerticalStrut(10));

            timer = new TimerCircle();
            timer.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(timer);

            add(Box.createVerticalStrut(10));

            JPanel badgeRow = new JPanel(new BorderLayout(10, 0));
            badgeRow.setOpaque(false);
            turnBadge = new BadgeLabel("Turno: --");
            badgeRow.add(turnBadge, BorderLayout.WEST);

            submitStatus = new StatusIndicator();
            badgeRow.add(submitStatus, BorderLayout.EAST);
            add(badgeRow);

            add(Box.createVerticalStrut(8));
            add(new GradientDivider(8));
            add(Box.createVerticalStrut(10));

            phaseLabel = new JLabel("Estado: --");
            phaseLabel.setFont(UiTheme.infoFont().deriveFont(Font.BOLD));
            phaseLabel.setForeground(UiTheme.TEXT_PRIMARY);
            roundLabel = new JLabel("Ronda: --");
            roundLabel.setFont(UiTheme.infoFont());
            roundLabel.setForeground(UiTheme.TEXT_MUTED);
            progressLabel = new JLabel("Envíos: --");
            progressLabel.setFont(UiTheme.infoFont());
            progressLabel.setForeground(UiTheme.TEXT_MUTED);

            add(phaseLabel);
            add(Box.createVerticalStrut(4));
            add(roundLabel);
            add(Box.createVerticalStrut(4));
            add(progressLabel);

            add(Box.createVerticalStrut(10));
            add(new GradientDivider(8));
            add(Box.createVerticalStrut(10));

            carousel = new NotificationCarousel();
            add(carousel);

            setBorder(new EmptyBorder(10, 10, 10, 10));
            setDisconnected();
        }

        void setCompact(boolean compact) {
            if (this.compact == compact) {
                return;
            }
            this.compact = compact;
            timer.setCompact(compact);
        }

        void setConnected(String playerName) {
            phaseLabel.setText("Estado: Conectado");
            turnBadge.setText("Turno: " + playerName);
            submitStatus.setState(SubmitState.WAITING);
            timer.setTotalSeconds(DEFAULT_ROUND_SECONDS);
            timer.setRemainingSeconds(DEFAULT_ROUND_SECONDS);
        }

        void setDisconnected() {
            stopCountdown();
            phaseLabel.setText("Estado: Desconectado");
            roundLabel.setText("Ronda: --");
            progressLabel.setText("Envíos: --");
            turnBadge.setText("Turno: --");
            submitStatus.setState(SubmitState.WAITING);
            timer.setTotalSeconds(DEFAULT_ROUND_SECONDS);
            timer.setRemainingSeconds(DEFAULT_ROUND_SECONDS);
        }

        void setPhaseLobby() {
            stopCountdown();
            phaseLabel.setText("Estado: Lobby");
            roundLabel.setText("Ronda: --");
            progressLabel.setText("Envíos: --");
            submitStatus.setState(SubmitState.WAITING);
        }

        void startRound(int round, int maxRounds, String targetOwner) {
            phaseLabel.setText("Estado: En ronda");
            roundLabel.setText("Ronda: " + round + "/" + maxRounds);
            progressLabel.setText("Envíos: 0/" + maxRounds);
            if (targetOwner != null && !targetOwner.isBlank()) {
                turnBadge.setText("Turno: " + targetOwner);
            }
            submitStatus.setState(SubmitState.WAITING);
            startCountdown(DEFAULT_ROUND_SECONDS);
        }

        void setRoundProgress(int round, int submitted, int total) {
            roundLabel.setText("Ronda: " + round);
            progressLabel.setText("Envíos: " + submitted + "/" + total);
        }

        void markSubmissionPending() {
            submitStatus.setState(SubmitState.SENDING);
        }

        void markSubmissionSent() {
            submitStatus.setState(SubmitState.SENT);
        }

        void finishGame() {
            stopCountdown();
            phaseLabel.setText("Estado: Finalizado");
            submitStatus.setState(SubmitState.SENT);
        }

        void pushNotification(String message) {
            carousel.push(message);
        }

        private void startCountdown(int seconds) {
            stopCountdown();
            total = Math.max(1, seconds);
            remaining = total;
            timer.setTotalSeconds(total);
            timer.setRemainingSeconds(remaining);
            countdown = new Timer(1000, _e -> {
                remaining = Math.max(0, remaining - 1);
                timer.setRemainingSeconds(remaining);
                if (remaining <= 0) {
                    stopCountdown();
                }
            });
            countdown.start();
        }

        private void stopCountdown() {
            if (countdown != null) {
                countdown.stop();
                countdown = null;
            }
        }
    }
}

