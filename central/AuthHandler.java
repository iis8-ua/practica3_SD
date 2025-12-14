package p3.central;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import p3.db.DBManager;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

public class AuthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if ("POST".equals(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            java.util.Scanner s = new java.util.Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String body = s.hasNext() ? s.next() : "";
            
            String cpId = extraerValor(body, "id");
            String token = extraerValor(body, "token");
            
            System.out.println("[AUTH] Petición de login para: " + cpId);

            String ipCliente = exchange.getRemoteAddress().getAddress().getHostAddress();
            String origen = ipCliente + " (" + cpId + ")";
            String claveAES = DBManager.validarYObtenerClave(cpId, token);
            
            if (claveAES != null) {
            	DBManager.registrarAuditoria(
            	        origen, 
            	        "AUTENTICACION", 
            	        "Login correcto: Token validado y clave entregada", 
            	        "EXITO"
            	);
            	
                String response = String.format("{\"clave\":\"%s\"}", claveAES);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                System.out.println("[AUTH] Clave entregada a " + cpId);
            } 
            else {
            	DBManager.registrarAuditoria(
            	        origen, 
            	        "AUTENTICACION", 
            	        "Fallo de Login: Token inválido o expirado", 
            	        "FALLO"
            	);
            	
                String error = "{\"error\":\"Credenciales inválidas\"}";
                exchange.sendResponseHeaders(401, error.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private String extraerValor(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1) {
            	return null;
            }
            
            start += search.length();
            
            while(start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
                start++;
            }
            
            int end = json.indexOf("\"", start);
            if (end == -1) {
            	return null;
            }
            
            return json.substring(start, end);
        } 
        catch (Exception e) {
            return null;
        }
    }
}