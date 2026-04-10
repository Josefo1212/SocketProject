package Server;

import DB.dbcomponent.DBComponent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private final DBComponent db;
    private final int port;
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    public GameServer(DBComponent db) {
        this(db, 5000);
    }

    public GameServer(DBComponent db, int port) {
        this.db = db;
        this.port = port;
    }

    public void start() {
        Thread acceptThread = new Thread(this::acceptLoop, "game-server-accept-loop");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("Servidor iniciado y conectado a la base de datos en puerto " + port + ".");
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                int clientId = clientCounter.incrementAndGet();
                clientPool.submit(() -> new ClientHandler(socket, clientId).handle());
            }
        } catch (IOException e) {
            System.err.println("[Server] Error en loop de conexiones: " + e.getMessage());
        }
    }
}
