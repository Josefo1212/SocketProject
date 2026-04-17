package Server;

import DB.dbcomponent.DBComponent;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private final int port;
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Integer, ClientHandler> handlersByClientId = new ConcurrentHashMap<>();
    private final GameLogic gameLogic;

    public GameServer(DBComponent db) {
        this(db, 5000);
    }

    public GameServer(DBComponent db, int port) {
        this.port = port;
        this.gameLogic = new GameLogic(db);
    }

    public void start() {
        final ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
        } catch (BindException e) {
            System.err.println("[Server] Puerto " + port + " ocupado: ya hay un servidor corriendo. No se iniciará otro.");
            return;
        } catch (IOException e) {
            System.err.println("[Server] No se pudo abrir el puerto " + port + ": " + e.getMessage());
            return;
        }

        Thread acceptThread = new Thread(() -> acceptLoop(serverSocket), "game-server-accept-loop");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("Servidor iniciado y conectado a la base de datos en puerto " + port + ".");
    }

    private void acceptLoop(ServerSocket serverSocket) {
        try (serverSocket) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                int clientId = clientCounter.incrementAndGet();

                ClientHandler handler = new ClientHandler(socket, clientId, gameLogic, this::sendToClient);
                handlersByClientId.put(clientId, handler);

                clientPool.submit(() -> {
                    try {
                        handler.handle();
                    } finally {
                        handlersByClientId.remove(clientId);
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("[Server] Error en loop de conexiones: " + e.getMessage());
        }
    }

    private void sendToClient(int clientId, String message) {
        ClientHandler handler = handlersByClientId.get(clientId);
        if (handler == null) {
            return;
        }
        handler.sendDirect(message);
    }
}
