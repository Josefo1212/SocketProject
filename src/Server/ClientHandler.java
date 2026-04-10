package Server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClientHandler {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JFrame frame;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField playerField;
    private final JTextArea gameLogArea;
    private final JTextField messageField;
    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JButton sendButton;

    private boolean connected;

    private ClientHandler(String defaultPlayerName, int windowXOffset) {
        frame = new JFrame("SocketProject - Cliente");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(860, 560);
        frame.setLocation(120 + windowXOffset, 120);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        hostField = new JTextField("127.0.0.1");
        portField = new JTextField("5000");
        playerField = new JTextField(defaultPlayerName);

        connectButton = new JButton("Conectar");
        disconnectButton = new JButton("Desconectar");
        sendButton = new JButton("Enviar");

        root.add(buildConfigPanel(), BorderLayout.NORTH);

        JPanel gamePanel = buildGamePanel();

        gameLogArea = new JTextArea();
        gameLogArea.setEditable(false);
        gameLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(gameLogArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Sala / Estado del juego"));

        messageField = new JTextField();
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel logPanel = new JPanel(new BorderLayout(0, 0));
        logPanel.add(scrollPane, BorderLayout.CENTER);
        logPanel.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, gamePanel, logPanel);
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(null);

        root.add(splitPane, BorderLayout.CENTER);
        frame.setContentPane(root);

        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        appendLog("Cliente listo. Configura y pulsa 'Conectar'.");
    }

    public static void createAndShow(String defaultPlayerName, int windowXOffset) {
        ClientHandler handler = new ClientHandler(defaultPlayerName, windowXOffset);
        handler.frame.setVisible(true);
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Configuracion del cliente"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Puerto:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.3;
        panel.add(portField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("Jugador:"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 0.6;
        panel.add(playerField, gbc);

        gbc.gridx = 6;
        gbc.weightx = 0;
        panel.add(connectButton, gbc);

        gbc.gridx = 7;
        panel.add(disconnectButton, gbc);

        return panel;
    }

    private JPanel buildGamePanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));

        JTextArea promptArea = new JTextArea();
        promptArea.setEditable(false);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setText("Aqui puedes mostrar la frase que el jugador debe continuar,\n" +
            "o la historia acumulada del turno.\n\n" +
            "(Base visual para integrar con sockets despues)");

        JTextArea roundInfoArea = new JTextArea();
        roundInfoArea.setEditable(false);
        roundInfoArea.setLineWrap(true);
        roundInfoArea.setWrapStyleWord(true);
        roundInfoArea.setText("Panel de ronda:\n" +
            "- Temporizador\n" +
            "- Turno actual\n" +
            "- Estado de envio\n" +
            "- Notificaciones");

        panel.add(wrapTitled(promptArea, "Zona de juego"));
        panel.add(wrapTitled(roundInfoArea, "Info de ronda"));

        return panel;
    }

    private JScrollPane wrapTitled(JTextArea area, String title) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createTitledBorder(title));
        return pane;
    }

    private void connect() {
        ClientConfig config;
        try {
            config = readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Configuracion invalida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        connected = true;
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        sendButton.setEnabled(true);

        hostField.setEnabled(false);
        portField.setEnabled(false);
        playerField.setEnabled(false);

        appendLog("Conectado (simulado) -> " + config.host + ":" + config.port + " como " + config.playerName);
    }

    private void disconnect() {
        connected = false;
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);

        hostField.setEnabled(true);
        portField.setEnabled(true);
        playerField.setEnabled(true);

        appendLog("Desconectado.");
    }

    private void sendMessage() {
        if (!connected) {
            appendLog("No puedes enviar: primero debes conectarte.");
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        appendLog(playerField.getText().trim() + ": " + message);
        messageField.setText("");
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
        gameLogArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + line + System.lineSeparator());
        gameLogArea.setCaretPosition(gameLogArea.getDocument().getLength());
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
}
