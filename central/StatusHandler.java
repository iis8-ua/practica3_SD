package p3.central;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import p3.db.DBManager;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class StatusHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("GET".equals(exchange.getRequestMethod())) {
            String jsonRespuesta = construirJsonCompleto();
            
            byte[] bytes = jsonRespuesta.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } 
        else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private String construirJsonCompleto() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"cps\": ").append(obtenerCPs()).append(",");
        json.append("\"drivers\": ").append(obtenerDrivers()).append(",");
        json.append("\"logs\": ").append(getAuditoriaJSON());
        json.append("}");
        return json.toString();
    }
    
    private String obtenerCPs() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT id, ubicacion, estado, precio_kwh, conductor_actual, registrado_central, token_sesion, temperatura FROM charging_point";
        
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                
                String cond = rs.getString("conductor_actual");
                String token = rs.getString("token_sesion");
                double temp = rs.getDouble("temperatura"); 
                if (rs.wasNull()) {
                	temp = -999; //indicar "Sin datos"
                }

                sb.append(String.format(java.util.Locale.US, 
                    "{\"id\":\"%s\",\"ubicacion\":\"%s\",\"estado\":\"%s\",\"precio\":%.2f,\"conductor\":\"%s\",\"registrado\":%b,\"token\":\"%s\",\"clima\":%.2f}",
                    rs.getString("id"), rs.getString("ubicacion"), rs.getString("estado"), 
                    rs.getDouble("precio_kwh"), cond == null ? "Libre" : cond,
                    rs.getBoolean("registrado_central"), token == null ? "-" : token,
                    temp
                ));
            }
        } 
        catch (SQLException e) { 
        	return "[]"; 
        }
        sb.append("]");
        return sb.toString();
    }

    private String obtenerDrivers() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT id, nombre, saldo FROM driver"; 
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format(Locale.US, "{\"id\":\"%s\",\"nombre\":\"%s\",\"saldo\":%.2f}",
                        rs.getString("id"), rs.getString("nombre"), rs.getDouble("saldo")));
            }
        } catch (SQLException e) { return "[]"; }
        sb.append("]");
        return sb.toString();
    }

    private String obtenerLogs() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT fecha, cp_id, tipo_evento, descripcion, ip_origen FROM event_log ORDER BY fecha DESC LIMIT 10";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                String desc = rs.getString("descripcion").replace("\n", " ").replace("\"", "'");
                String ip = rs.getString("ip_origen");
                if (ip == null) {
                	ip = "-";
                }
                sb.append(String.format("{\"fecha\":\"%s\",\"origen\":\"%s\",\"evento\":\"%s\",\"mensaje\":\"%s\",\"ip\":\"%s\"}",
                        rs.getTimestamp("fecha"), 
                        rs.getString("cp_id"), 
                        rs.getString("tipo_evento"), 
                        desc,
                        ip));
            }
        } 
        catch (SQLException e) { 
        	return "[]"; 
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String getAuditoriaJSON() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT fecha, origen, tipo_evento, descripcion, resultado FROM auditoria ORDER BY fecha DESC LIMIT 10";
        
        try (java.sql.Connection conn = p3.db.DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
             
            boolean primero = true;
            while (rs.next()) {
                if (!primero) {
                	sb.append(",");
                }
                
                sb.append(String.format(
                    "{\"fecha\":\"%s\", \"origen\":\"%s\", \"tipo\":\"%s\", \"descripcion\":\"%s\", \"resultado\":\"%s\"}",
                    rs.getTimestamp("fecha").toString(),
                    rs.getString("origen"),
                    rs.getString("tipo_evento"),
                    rs.getString("descripcion").replace("\"", "'"),
                    rs.getString("resultado")
                ));
                primero = false;
            }
        } 
        catch (Exception e) {
            System.err.println("Error leyendo auditoria: " + e.getMessage());
        }
        
        sb.append("]");
        return sb.toString();
    }
}

