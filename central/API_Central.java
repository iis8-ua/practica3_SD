package p3.central; // Asegúrate de que el paquete es correcto

import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class API_Central {

    private HttpServer server;
    private KafkaProducer<String, String> productor;

    // Recibimos el productor porque WeatherHandler lo necesita
    public API_Central(KafkaProducer<String, String> productor) {
        this.productor = productor;
    }

    public void iniciar(int puerto) {
        try {
            server = HttpServer.create(new InetSocketAddress(puerto), 0);
            
            server.createContext("/api/alertas", new WeatherHandler(productor));
            server.createContext("/api/estado", new StatusHandler());
            
            server.setExecutor(null);
            server.start();
            
            System.out.println("--------------------------------------------------");
            System.out.println("API CENTRAL iniciada en puerto " + puerto);
            System.out.println(" -> Alertas: http://localhost:" + puerto + "/api/alertas");
            System.out.println(" -> Estado:  http://localhost:" + puerto + "/api/estado");
            System.out.println("--------------------------------------------------");
            
        } 
        catch (IOException e) {
            System.err.println("Error iniciando API REST: " + e.getMessage());
        }
    }

    public void detener() {
        if (server != null) {
            server.stop(0);
            System.out.println("API REST detenida.");
        }
    }
}