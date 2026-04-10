package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientHandler {
	private final Socket socket;
	private final int clientId;

	public ClientHandler(Socket socket, int clientId) {
		this.socket = socket;
		this.clientId = clientId;
	}

	public void handle() {
		String clientAddress = String.valueOf(socket.getRemoteSocketAddress());
		System.out.println("[Server] Cliente #" + clientId + " conectado desde " + clientAddress);

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			while (reader.readLine() != null) {
				// Placeholder: por ahora solo mantenemos viva la conexión.
			}
		} catch (IOException e) {
			System.err.println("[Server] Error con cliente #" + clientId + ": " + e.getMessage());
		} finally {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
			System.out.println("[Server] Cliente #" + clientId + " desconectado.");
		}
	}
}
