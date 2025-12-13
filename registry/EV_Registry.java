package p3.registry;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import p3.db.DBManager;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;


public class EV_Registry {

    private static final int PUERTO = 4444;

    public static void main(String[] args) {
        try {
            // 1. Conectar a la Base de Datos
            DBManager.connect();

            char[] password = "password123".toCharArray(); 
            KeyStore ks = KeyStore.getInstance("JKS");
          
            FileInputStream fis = new FileInputStream("p3/registry/registry.jks");
            ks.load(fis, password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            
            //HttpServer server = HttpServer.create(new InetSocketAddress(PUERTO), 0);
            HttpsServer server = HttpsServer.create(new InetSocketAddress(PUERTO), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext c = getSSLContext();
                        SSLEngine engine = c.createSSLEngine();
                        
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());
                        
                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                        
                    } 
                    catch (Exception ex) {
                        System.err.println("Error configurando conexión HTTPS: " + ex.getMessage());
                    }
                }
            });
            

            server.createContext("/api/registro", new RegistroHandler());
            
            server.setExecutor(null);
            server.start();
            
            System.out.println("--- EV_REGISTRY Iniciado ---");
            System.out.println("   >> Puerto: " + PUERTO);
            System.out.println("   >> Esperando peticiones de CPs...");
            
        } 
        catch (Exception e) {
            System.err.println("Error fatal iniciando Registry: " + e.getMessage());
            e.printStackTrace();
        }
    }
}