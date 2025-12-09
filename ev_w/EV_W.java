package p3.ev_w;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
// Necesitarás una librería para parsear JSON (org.json o Gson) o hacerlo a mano con String methods si no puedes usar libs externas.

public class EV_W {
	private static final String API_KEY = "a2331b49f4c9d2656d837c291d852c12";
    private static final String CITY = "Alicante,ES"; // O la ciudad que quieras monitorear
    private static final String CENTRAL_URL = "http://localhost:8080/api/weather-alert"; // URL futura de tu Central
    
    // Umbral de temperatura (0ºC = 273.15K)
    private static final double UMBRAL_FREEZE_KELVIN = 273.15; 

    public static void main(String[] args) {
        System.out.println("Iniciando Weather Control Office (EV_W)...");
        
        Timer timer = new Timer();
        // Ejecutar cada 4 segundos (4000 ms) como pide el enunciado
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkWeather();
            }
        }, 0, 4000);
    }

    private static void checkWeather() {
        try {
            // 1. Consultar OpenWeather
            String urlString = "https://api.openweathermap.org/data/2.5/weather?q=" + CITY + "&appid=" + API_KEY;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            // 2. Parsear temperatura (Ejemplo básico buscando la cadena, idealmente usar GSON/Jackson)
            String json = result.toString();
            double temp = extractTempFromJSON(json);
            
            System.out.println("Temperatura actual en " + CITY + ": " + (temp - 273.15) + "ºC");

            // 3. Notificar a Central si hace frío
            if (temp < UMBRAL_FREEZE_KELVIN) {
                notifyCentral(true);
            } else {
                notifyCentral(false); // Notificar que todo está OK (para restaurar servicio)
            }

        } catch (Exception e) {
            System.err.println("Error consultando el clima: " + e.getMessage());
        }
    }

    private static void notifyCentral(boolean alertaFrio) {
        // Aquí implementarás la llamada POST a la API de la Central (Paso 3)
        // Por ahora, solo imprime el log.
        if (alertaFrio) {
            System.out.println("ALERTA: Temperatura bajo cero. Enviando petición de PARADA a Central...");
        } else {
            System.out.println("CLIMA OK. Enviando estado NORMAL a Central...");
        }
    }

    // Método 'sucio' para sacar la temp sin librerías externas complejas
    private static double extractTempFromJSON(String json) {
        try {
            // Busca "temp":282.55,
            String search = "\"temp\":";
            int index = json.indexOf(search);
            if (index != -1) {
                int start = index + search.length();
                int end = json.indexOf(",", start);
                String tempStr = json.substring(start, end);
                return Double.parseDouble(tempStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 300.0; // Valor seguro por defecto
    }
}