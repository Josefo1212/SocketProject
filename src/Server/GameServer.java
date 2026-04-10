package Server;

import dbcomponent.DBComponent;

public class GameServer {
	private final DBComponent db;

	public GameServer(DBComponent db) {
		this.db = db;
	}

	public void start() {
		System.out.println("Servidor iniciado y conectado a la base de datos.");
		// Aquí iría el ciclo principal del servidor
	}
}
