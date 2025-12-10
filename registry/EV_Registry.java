package p3.registry;

import com.sun.net.httpserver.HttpServer;
import p3.db.DBManager;

import java.net.InetSocketAddress;

public class EV_Registry {

    private static final int PUERTO = 4444;

    public static void main(String[] args) {
        try {
            // 1. Conectar a la Base de Datos
            DBManager.connect();

            // 2. Iniciar Servidor HTTP
            HttpServer server = HttpServer.create(new InetSocketAddress(PUERTO), 0);
            
            // 3. Definir el endpoint usando la clase externa
            server.createContext("/api/registro", new RegistroHandler());
            
            server.setExecutor(null);
            server.start();
            
            System.out.println("--- EV_REGISTRY Iniciado ---");
            System.out.println("   >> Puerto: " + PUERTO);
            System.out.println("   >> Esperando peticiones de CPs...");
            
        } catch (Exception e) {
            System.err.println("Error fatal iniciando Registry: " + e.getMessage());
        }
    }
}