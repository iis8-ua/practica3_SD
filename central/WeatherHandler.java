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

    public WeatherHandler(KafkaProducer<String, String> productor) {
        this.productor = productor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        
        if ("POST".equals(exchange.getRequestMethod())) {
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

            boolean esAlertaFrio = body.contains("\"alerta\": true") || body.contains("\"alerta\":true");
            String ciudad = extraerCiudad(body);
            double temperatura = extraerDouble(body, "temperatura");
            
            actualizarTemperaturaBD(ciudad, temperatura);
            gestionarAccionClimatica(ciudad, esAlertaFrio);

            String response = "Alerta procesada correctamente";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } 
        else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
    
    private void gestionarAccionClimatica(String ciudad, boolean esFrio) {
        String sql = "SELECT id FROM charging_point WHERE ubicacion LIKE ?";
        
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, "%" + ciudad + "%");
            ResultSet rs = ps.executeQuery();
            
            while(rs.next()) {
                String cpId = rs.getString("id");
                String comando;
                
                if (esFrio) {
                    System.out.println("ALERTA FRÍO: Enviando PARADA a " + cpId);
                    comando = "Parada_Emergencia";
                } else {
                    System.out.println("CLIMA OK: Restableciendo " + cpId);
                    comando = "Reanudar";
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

    private String extraerCiudad(String json) {
        try {
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
    
    private void actualizarTemperaturaBD(String ciudad, double temp) {
        String sql = "UPDATE charging_point SET temperatura = ? WHERE ubicacion LIKE ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, temp);
            ps.setString(2, "%" + ciudad + "%");
            ps.executeUpdate();
        } 
        catch (SQLException e) {
            System.err.println("Error guardando temperatura: " + e.getMessage());
        }
    }
    
    private double extraerDouble(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":");
            if (idx == -1) return 0.0;
            int start = idx + key.length() + 3;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return Double.parseDouble(json.substring(start, end).trim());
        } 
        catch (Exception e) { 
        	return 0.0; 
        }
    }
}