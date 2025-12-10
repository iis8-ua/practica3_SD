package p3.registry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import p3.db.DBManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Scanner;
import java.util.UUID;

public class RegistroHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if ("POST".equals(exchange.getRequestMethod())) {
            System.out.println("\n[REGISTRY] Nueva petición recibida.");

            // A. Leer body
            InputStream is = exchange.getRequestBody();
            @SuppressWarnings("resource")
            Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String body = s.hasNext() ? s.next() : "";
            
            System.out.println("   Datos: " + body);

            // B. Extraer ID
            String cpId = extraerValorJson(body, "id");

            if (cpId != null) {
                // C. Generar credenciales
                String nuevoToken = UUID.randomUUID().toString().substring(0, 8);
                String nuevaClave = "AES-" + UUID.randomUUID().toString().substring(0, 8);

                // D. Guardar en BD
                boolean exito = registrarEnBD(cpId, nuevoToken, nuevaClave);

                if (exito) {
                    String jsonRespuesta = String.format(
                        "{\"status\":\"OK\", \"token\":\"%s\", \"clave\":\"%s\"}", 
                        nuevoToken, nuevaClave
                    );
                    enviarRespuesta(exchange, 200, jsonRespuesta);
                    System.out.println("   -> REGISTRO OK: Credenciales enviadas a " + cpId);
                } 
                else {
                    enviarRespuesta(exchange, 500, "{\"error\":\"Error interno o CP no existe\"}");
                    System.err.println("   -> ERROR: No se pudo guardar en BD");
                }
            } 
            else {
                enviarRespuesta(exchange, 400, "{\"error\":\"Falta ID\"}");
            }
        } 
        else {
            enviarRespuesta(exchange, 405, "Metodo no permitido");
        }
    }

    private boolean registrarEnBD(String cpId, String token, String clave) {
        String sql = "UPDATE charging_point SET registrado_central=TRUE, token_sesion=?, clave_cifrado=? WHERE id=?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, token);
            ps.setString(2, clave);
            ps.setString(3, cpId);
            return ps.executeUpdate() > 0;
            
        } 
        catch (Exception e) {
            System.err.println("[DB Error] " + e.getMessage());
            return false;
        }
    }

    private void enviarRespuesta(HttpExchange exchange, int codigo, String respuesta) throws IOException {
        byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String extraerValorJson(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length();
            int q1 = json.indexOf("\"", start);
            int q2 = json.indexOf("\"", q1 + 1);
            if (q1 == -1 || q2 == -1) return null;
            return json.substring(q1 + 1, q2);
        } 
        catch (Exception e) { 
        	return null; 
        }
    }
}