package p3.db;

import java.sql.*;

/**
 * DBManager - Gestor centralizado de conexión a la base de datos.
 *
 * Compatible con MySQL/MariaDB.
 * 
 * Configuración por defecto:
 *   - Host: localhost
 *   - Puerto: 3306
 *   - Base de datos: evcharging_db
 *   - Usuario: root
 *   - Contraseña: (vacía)
 *
 * Puedes modificar estos valores según tu entorno local.
 */
public class DBManager {

    // === CONFIGURACIÓN DE CONEXIÓN ===
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3307;
    private static final String DB_NAME = "ev_charging_system";
    private static final String DB_USER = "evuser";
    private static final String DB_PASS = "evpass";
    
    private static final String DB_URL =
            String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Europe/Madrid&allowPublicKeyRetrieval=true",
                    DB_HOST, DB_PORT, DB_NAME);

    private static Connection connection = null;

    /**
     * Inicializa la conexión a la base de datos.
     * Se llama una vez desde el inicio de los módulos (Driver, Central, CP, etc.).
     */
    public static void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                //System.out.println("[DB] Conectado a la base de datos correctamente.");
            }
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver JDBC no encontrado: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("[DB] Error conectando a la BD: " + e.getMessage());
        }
    }

    /**
     * Devuelve una conexión activa.
     * Si la conexión actual no es válida, intenta reconectar automáticamente.
     */
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connect();
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error validando conexión: " + e.getMessage());
            connect();
        }
        return connection;
    }
    
    public static String getClaveCifrado(String cpId) {
    	String sql = "SELECT clave_cifrado FROM charging_point WHERE id = ?";
    	
    	try (Connection conn = getConnection();
           PreparedStatement ps = conn.prepareStatement(sql)) {
           
           ps.setString(1, cpId);
           try (ResultSet rs = ps.executeQuery()) {
               if (rs.next()) {
                   return rs.getString("clave_cifrado");
               }
           }
    	}
    	catch (Exception e) {
            System.err.println("[DB] Error recuperando clave de cifrado: " + e.getMessage());
        }
    	return null;
    }
    
    public static boolean revocarCredenciales(String cpId) {
    	String sql = "UPDATE charging_point SET token_sesion=NULL, clave_cifrado=NULL, registrado_central=FALSE, estado='DESCONECTADO' WHERE id=?";
        
    	String origenIP = "127.0.0.1";
        try {
            origenIP = java.net.InetAddress.getLocalHost().getHostAddress();
        } 
        catch (Exception e) {
        	
        }
    	
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, cpId);
            int filasAfectadas = ps.executeUpdate();
            
            if (filasAfectadas > 0) {
                System.out.println("[DB] Credenciales eliminadas para " + cpId);
                
                registrarAuditoria(
                        origenIP,              
                        "REVOCACION", 
                        "Claves borradas manualmente para " + cpId, 
                        "EXITO"
                );
                
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("[DB] Error revocando credenciales: " + e.getMessage());
        }
        return false;
    }
    
    
    public static String validarYObtenerClave(String cpId, String token) {
    	String sql = "SELECT clave_cifrado FROM charging_point WHERE id=? AND token_sesion=? AND registrado_central=TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, cpId);
            ps.setString(2, token);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                	System.out.println("[DB] Query ejecutada. Token coincide.");
                    return rs.getString("clave_cifrado");
                }
            }
        } 
        catch (SQLException e) {
            System.err.println("[DB] Error validando credenciales: " + e.getMessage());
        }
        
        System.out.println("[DB] Token no coincidente o CP no registrado para ID: " + cpId);
        return null; 
    }
    
    public static String obtenerOrigen(String cpId) {
        String sql = "SELECT origen FROM auditoria WHERE origen LIKE ? ORDER BY id DESC LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, "%(" + cpId + ")"); 
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("origen");
                }
            }
        } 
        catch (Exception e) {
        	
        }
        
        return "IP_DESCONOCIDA (" + cpId + ")"; 
    }

    public static void registrarAuditoria(String origen, String tipo, String descripcion, String resultado) {
        new Thread(() -> {
        	
        	String url = "jdbc:mysql://localhost:3307/ev_charging_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Madrid";
            String user = "evuser";
            String pass = "evpass";
        	
            String sql = "INSERT INTO auditoria (origen, tipo_evento, descripcion, resultado) VALUES (?, ?, ?, ?)";
            try (Connection conn =DriverManager.getConnection(url, user, pass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, origen);
                ps.setString(2, tipo);
                ps.setString(3, descripcion);
                ps.setString(4, resultado);
                
                ps.executeUpdate();
            } 
            catch (Exception e) {
                System.err.println("Error guardando auditoría: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Cierra la conexión de forma segura.
     */
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Conexión cerrada.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error cerrando conexión: " + e.getMessage());
        }
    }
}