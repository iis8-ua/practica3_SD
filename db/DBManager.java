package p2.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
    private static final int DB_PORT = 3306;
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