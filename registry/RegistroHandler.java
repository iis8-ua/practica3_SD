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

        if ("POST".equals(exchange.getRequestMethod())) {
            System.out.println("\n[REGISTRY] Petición de registro recibida.");

            // A. Leer body
            InputStream is = exchange.getRequestBody();
            @SuppressWarnings("resource")
            Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String body = s.hasNext() ? s.next() : "";
            
            // B. Extraer datos del JSON
            String cpId = extraerValorJson(body, "id");
            String firma = extraerValorJson(body, "firma");
            String certString = extraerValorJson(body, "certificado");

            // VALIDACIÓN DE SEGURIDAD (RSA / OPENSSL)
            if (cpId != null && firma != null && certString != null) {
                
                boolean esValido = verificarIdentidad(cpId, firma, certString);
                
                if (esValido) {
                    System.out.println("Identidad VERIFICADA para " + cpId);
                    
                    // C. Generar credenciales
                    String nuevoToken = UUID.randomUUID().toString().substring(0, 8);
                    String nuevaClave = "AES-" + UUID.randomUUID().toString().substring(0, 8);

                    // D. Guardar en BD
                    if (registrarEnBD(cpId, nuevoToken, nuevaClave)) {
                        String jsonRespuesta = String.format(
                            "{\"status\":\"OK\", \"token\":\"%s\", \"clave\":\"%s\"}", 
                            nuevoToken, nuevaClave
                        );
                        enviarRespuesta(exchange, 200, jsonRespuesta);
                        System.out.println("   -> Credenciales enviadas.");
                    } else {
                        enviarRespuesta(exchange, 500, "{\"error\":\"Error de Base de Datos\"}");
                    }
                } else {
                    System.err.println("FIRMA INVÁLIDA: Intento de registro fraudulento para " + cpId);
                    enviarRespuesta(exchange, 403, "{\"error\":\"Firma digital invalida\"}");
                }
            } else {
                enviarRespuesta(exchange, 400, "{\"error\":\"Faltan datos (id, firma o certificado)\"}");
            }
        } else {
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

    private boolean registrarEnBD(String cpId, String token, String clave) {
        String sql = "UPDATE charging_point SET registrado_central=TRUE, token_sesion=?, clave_cifrado=? WHERE id=?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, clave);
            ps.setString(3, cpId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
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
            while(start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
            
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }
}
