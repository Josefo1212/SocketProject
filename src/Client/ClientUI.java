package Client;

import common.JsonMessage;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

    private final JFrame frame;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField playerField;
    private final JTextArea gameLogArea;
    private final JTextArea promptArea;
    private final JTextArea storyArea;
    private final JTextArea roundInfoArea;
    private final JTextField messageField;
    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JButton sendButton;
    private final JLabel typingIndicator;
    private final Timer typingBlinkTimer;

    private volatile boolean connected;
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread socketReaderThread;

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
        UiTheme.styleButton(connectButton);
        UiTheme.styleButton(disconnectButton);
        UiTheme.styleButton(sendButton);

        promptArea = createReadOnlyArea(UiTheme.bodyFont(), new Color(0x0A, 0x19, 0x2F));
        storyArea = createReadOnlyArea(UiTheme.infoFont(), new Color(230, 236, 245));
        roundInfoArea = createReadOnlyArea(UiTheme.infoFont(), UiTheme.TEXT_PRIMARY);
        gameLogArea = createReadOnlyArea(UiTheme.codeFont(), UiTheme.TEXT_PRIMARY);
        messageField = new JTextField();
        UiTheme.styleInput(messageField);

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
        card.add(buildTopHeader(), BorderLayout.NORTH);
        card.add(buildMainContent(), BorderLayout.CENTER);

        GridBagConstraints cardGbc = new GridBagConstraints();
        cardGbc.gridx = 0;
        cardGbc.gridy = 0;
        cardGbc.weightx = 1;
        cardGbc.weighty = 1;
        cardGbc.fill = GridBagConstraints.BOTH;
        root.add(card, cardGbc);

        frame.setContentPane(root);

        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);

        connectButton.addActionListener(_e -> connect());
        disconnectButton.addActionListener(_e -> disconnect());
        sendButton.addActionListener(_e -> sendMessage());
        messageField.addActionListener(_e -> sendMessage());

        appendLog("Cliente listo. Comandos: /create CODIGO, /join CODIGO, /leave, /ping, /quit");
        promptArea.setText("Escribe una frase creativa para continuar la historia.\nCuando llegue el evento de ronda, este panel mostrara la frase objetivo.");
        storyArea.setText("Historia acumulada:\n- Aun no hay fragmentos en esta ronda.");
        roundInfoArea.setText("Panel de ronda:\n- Estado: Esperando conexion\n- Ronda: --\n- Temporizador: --\n- Turno: --");
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
        top.add(buildConfigPanel(), BorderLayout.CENTER);
        return top;
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

    private JPanel buildMainContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.weightx = 0.60;
        left.weighty = 1;
        left.insets = new Insets(0, 0, 0, 10);
        left.fill = GridBagConstraints.BOTH;
        content.add(buildGamePanel(), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 0.40;
        right.weighty = 1;
        right.fill = GridBagConstraints.BOTH;
        content.add(buildRoundAndChatPanel(), right);

        return content;
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
        promptCard.add(new JScrollPane(promptArea), BorderLayout.CENTER);

        JPanel storyCard = new JPanel(new BorderLayout());
        storyCard.setOpaque(true);
        storyCard.setBackground(new Color(20, 42, 68, 220));
        storyCard.setBorder(new CompoundBorder(new LineBorder(new Color(58, 123, 213, 180), 1, true), new EmptyBorder(10, 10, 10, 10)));

        JLabel storyTitle = new JLabel("Historia acumulada");
        storyTitle.setFont(UiTheme.subtitleFont());
        storyTitle.setForeground(UiTheme.TEXT_PRIMARY);
        storyCard.add(storyTitle, BorderLayout.NORTH);
        storyCard.add(new JScrollPane(storyArea), BorderLayout.CENTER);

        JSplitPane gameSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, promptCard, storyCard);
        gameSplit.setResizeWeight(0.5);
        gameSplit.setDividerSize(8);
        gameSplit.setBorder(null);
        gameSplit.setOpaque(false);

        panel.add(top, BorderLayout.NORTH);
        panel.add(gameSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRoundAndChatPanel() {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(10, 10), 24, UiTheme.PANEL_BG);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        UiTheme.styleTitleBorder(roundInfoArea, "Info de ronda");
        JScrollPane roundScroll = new JScrollPane(roundInfoArea);
        roundScroll.setOpaque(false);
        roundScroll.getViewport().setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(roundScroll, BorderLayout.CENTER);

        UiTheme.styleTitleBorder(gameLogArea, "Sala / Chat");
        JScrollPane logScroll = new JScrollPane(gameLogArea);
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        bottom.add(logScroll, BorderLayout.CENTER);
        bottom.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        rightSplit.setResizeWeight(0.36);
        rightSplit.setBorder(null);
        rightSplit.setOpaque(false);

        panel.add(rightSplit, BorderLayout.CENTER);
        return panel;
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

        appendLog("Conectado -> " + config.host + ":" + config.port + " como " + config.playerName);
        roundInfoArea.setText("Panel de ronda:\n- Estado: Conectado\n- Ronda: 1\n- Temporizador: --\n- Turno: " + config.playerName);
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

        appendLog("Desconectado.");
        roundInfoArea.setText("Panel de ronda:\n- Estado: Desconectado\n- Ronda: --\n- Temporizador: --\n- Turno: --");
    }

    private void sendMessage() {
        if (!connected) {
            appendLog("No puedes enviar: primero debes conectarte.");
            return;
        }

        String input = messageField.getText().trim();
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
            appendLog("Tu -> " + JsonMessage.stringify(payload));
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
                        appendLog("Server(raw): " + line);
                        continue;
                    }
                    appendLog("Server -> " + formatIncoming(message));
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

    private String formatIncoming(Map<String, String> message) {
        String type = message.getOrDefault("type", "");
        String event = message.getOrDefault("event", "");

        if ("ERROR".equalsIgnoreCase(type)) {
            return "ERROR " + message.getOrDefault("code", "UNKNOWN") + ": " + message.getOrDefault("message", "");
        }

        if ("EVENT".equalsIgnoreCase(type) && "CHAT".equalsIgnoreCase(event)) {
            return "[" + message.getOrDefault("roomCode", "?") + "] " +
                    message.getOrDefault("from", "?") + ": " +
                    message.getOrDefault("message", "");
        }

        return JsonMessage.stringify(message);
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
            gameLogArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + line + System.lineSeparator());
            gameLogArea.setCaretPosition(gameLogArea.getDocument().getLength());
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
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int arc = 24;
            Shape shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, arc, arc);

            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRoundRect(8, 12, getWidth() - 8, getHeight() - 8, arc, arc);

            g2.setColor(UiTheme.GLASS_BG);
            g2.fill(shape);

            g2.setColor(UiTheme.GLASS_BORDER);
            g2.draw(shape);
            g2.dispose();
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
}

