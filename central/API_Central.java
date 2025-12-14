package p3.central; // Asegúrate de que el paquete es correcto

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import org.apache.kafka.clients.producer.KafkaProducer;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;

public class API_Central {

    private HttpsServer server;
    private KafkaProducer<String, String> productor;

    // Recibimos el productor porque WeatherHandler lo necesita
    public API_Central(KafkaProducer<String, String> productor) {
        this.productor = productor;
    }

    public void iniciar(int puerto) {
        try {
        	char[] password = "password123".toCharArray(); 
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("registry/registry.jks");
            ks.load(fis, password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            
            server = HttpsServer.create(new InetSocketAddress(puerto), 0);
            
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
                        System.err.println("Error config HTTPS: " + ex.getMessage());
                    }
                }
            });
            
            server.createContext("/api/alertas", new WeatherHandler(productor));
            server.createContext("/api/estado", new StatusHandler());
            server.createContext("/api/login", new AuthHandler());
            
            server.setExecutor(null);
            server.start();
            
            System.out.println("--------------------------------------------------");
            System.out.println("API CENTRAL iniciada en puerto " + puerto);
            System.out.println(" -> Alertas: https://localhost:" + puerto + "/api/alertas");
            System.out.println(" -> Estado:  https://localhost:" + puerto + "/api/estado");
            System.out.println(" -> Login:  https://localhost:" + puerto + "/api/login");
            System.out.println("--------------------------------------------------");
            
        } 
        catch (Exception e) {
            System.err.println("Error iniciando API REST: " + e.getMessage());
        }
    }

    public void detener() {
        if (server != null) {
            server.stop(0);
            System.out.println("API REST detenida.");
        }
    }
}