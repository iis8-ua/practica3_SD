package p3.registry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import p3.db.DBManager;
import p3.common.CryptoUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Scanner;
import java.util.UUID;

public class RegistroHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        
        String metodo = exchange.getRequestMethod();
        System.out.println("\n[REGISTRY] Petición recibida: " + metodo);

        // 1. LEER BODY
        InputStream is = exchange.getRequestBody();
        Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        String body = s.hasNext() ? s.next() : "";
        s.close();

        // 2. EXTRAER DATOS BÁSICOS
        String cpId = extraerValorJson(body, "id");
        String firma = extraerValorJson(body, "firma");
        String certString = extraerValorJson(body, "certificado");

        // 3. VALIDACIÓN DE SEGURIDAD
        if (cpId == null || firma == null || certString == null) {
            enviarRespuesta(exchange, 400, "{\"error\":\"Faltan datos de seguridad\"}");
            return;
        }

        if (!verificarIdentidad(cpId, firma, certString)) {
            System.err.println("FIRMA INVÁLIDA para " + cpId);
            enviarRespuesta(exchange, 403, "{\"error\":\"Firma digital invalida\"}");
            return;
        }

        if ("POST".equals(metodo)) {
            
            String ubicacion = extraerValorJson(body, "ubicacion");
            if(ubicacion == null) {
            	ubicacion = "Desconocida";
            }

            String nuevoToken = UUID.randomUUID().toString().substring(0, 8);
            String nuevaClave = "AES-" + UUID.randomUUID().toString().substring(0, 8);

            if (registrarEnBD(cpId, ubicacion, nuevoToken, nuevaClave)) {
                String json = String.format("{\"status\":\"OK\", \"token\":\"%s\", \"clave\":\"%s\"}", nuevoToken, nuevaClave);
                enviarRespuesta(exchange, 200, json);
                System.out.println("ALTA OK: " + cpId + " en " + ubicacion);
            } 
            else {
                enviarRespuesta(exchange, 500, "{\"error\":\"Error BD\"}");
            }

        } 
        else if ("DELETE".equals(metodo)) {
            if (darDeBajaEnBD(cpId)) {
                enviarRespuesta(exchange, 200, "{\"status\":\"BAJA_OK\"}");
                System.out.println("BAJA OK: " + cpId + " ha sido eliminado del registro.");
            } 
            else {
                enviarRespuesta(exchange, 500, "{\"error\":\"Error BD o CP no existe\"}");
            }

        } 
        
        else {
            enviarRespuesta(exchange, 405, "Metodo no permitido");
        }
    }
    
    private boolean verificarIdentidad(String cpId, String firma, String certRaw) {
        try {
            X509Certificate cert = CryptoUtils.cargarCertificadoDesdeString(certRaw);
            
            String principal = cert.getSubjectX500Principal().getName();
            if (!principal.contains("CN=" + cpId)) {
                System.err.println("   Mismatch: Certificado pertenece a " + principal);
                return false;
            }

            return CryptoUtils.verificarFirmaRSA(cpId, firma, cert);
            
        } 
        catch (Exception e) {
            System.err.println("   Error verificando firma: " + e.getMessage());
            return false;
        }
    }

    private boolean registrarEnBD(String cpId, String ubicacion, String token, String clave) {
    	String sql = "UPDATE charging_point SET registrado_central=TRUE, estado='ACTIVADO', ubicacion=?, token_sesion=?, clave_cifrado=? WHERE id=?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ubicacion);
            ps.setString(2, token);
            ps.setString(3, clave);
            ps.setString(4, cpId);
            return ps.executeUpdate() > 0;
        } 
        catch (Exception e) { 
        	return false; 
        }
    }
    
    private boolean darDeBajaEnBD(String cpId) {
        String sql = "UPDATE charging_point SET registrado_central=FALSE, token_sesion=NULL, clave_cifrado=NULL, estado='DESCONECTADO' WHERE id=?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpId);
            return ps.executeUpdate() > 0;
        } 
        catch (Exception e) { 
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
            while(start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
            
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }
}
