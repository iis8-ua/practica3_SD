package p3.central;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import p3.db.DBManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WeatherHandler implements HttpHandler {
    
    private KafkaProducer<String, String> productor;

    // Pasamos el productor en el constructor para poder enviar comandos a los CPs
    public WeatherHandler(KafkaProducer<String, String> productor) {
        this.productor = productor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Configurar cabeceras CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        
        if ("POST".equals(exchange.getRequestMethod())) {
            // 1. Leer JSON
        	InputStream is = exchange.getRequestBody();
        	StringBuilder sb = new StringBuilder();
        	try (java.io.BufferedReader reader = new java.io.BufferedReader(
        	        new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
        	    String line;
        	    while ((line = reader.readLine()) != null) {
        	        sb.append(line);
        	    }
        	}
        	String body = sb.toString();
        	System.out.println("[API REST] Alerta recibida de EV_W: " + body);

            // 2. Parseo manual básico del JSON
            boolean esAlertaFrio = body.contains("\"alerta\": true") || body.contains("\"alerta\":true");
            String ciudad = extraerCiudad(body);

            // 3. Lógica de negocio (Consultar BD y Parar CPs)
            gestionarAccionClimatica(ciudad, esAlertaFrio);

            // 4. Responder OK
            String response = "Alerta procesada correctamente";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
    
    private void gestionarAccionClimatica(String ciudad, boolean esFrio) {
        // Buscamos en la BD qué CPs están en esa ciudad (o contienen el nombre de la ciudad)
        String sql = "SELECT id FROM charging_point WHERE ubicacion LIKE ?";
        
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, "%" + ciudad + "%"); // Buscamos coincidencias parciales
            ResultSet rs = ps.executeQuery();
            
            while(rs.next()) {
                String cpId = rs.getString("id");
                String comando;
                
                if (esFrio) {
                    System.out.println("ALERTA FRÍO: Enviando PARADA a " + cpId);
                    comando = "Parada_Emergencia"; // O "Parar", según tu lógica
                } else {
                    System.out.println("CLIMA OK: Restableciendo " + cpId);
                    comando = "Reanudar"; // O el comando para volver a activar
                }
                
                enviarComandoKafka(cpId, comando);
            }
            
        } catch (SQLException e) {
            System.err.println("Error consultando CPs afectados por clima: " + e.getMessage());
        }
    }
    
    private void enviarComandoKafka(String cpId, String comando) {
        try {
            if (productor != null) {
                ProducerRecord<String, String> record = new ProducerRecord<>("comandos-cp", cpId, comando);
                productor.send(record);
            }
        } 
        catch (Exception e) {
            System.err.println("Error enviando comando a Kafka: " + e.getMessage());
        }
    }

    // Helper simple para sacar el nombre de la ciudad del JSON
    private String extraerCiudad(String json) {
        try {
            // Busca "ciudad":"Valor"
            String key = "\"ciudad\":";
            int idx = json.indexOf(key);
            if (idx != -1) {
                int start = json.indexOf("\"", idx + key.length()) + 1;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (Exception e) {
            return "Desconocida";
        }
        return "Desconocida";
    }
}