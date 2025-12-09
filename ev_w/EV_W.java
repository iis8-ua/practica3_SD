package p3.ev_w;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class EV_W {
    private static final String API_KEY = "a2331b49f4c9d2656d837c291d852c12"; 
    
    private static volatile String ciudadActual = "Alicante,ES"; 
    
    // Umbral de temperatura (0ºC = 273.15K)
    private static final double UMBRAL_FREEZE_KELVIN = 273.15; 
    
    private static final String CENTRAL_API_URL = "http://localhost:5000/api/alertas";

    public static void main(String[] args) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkWeather();
            }
        }, 0, 4000);

        Scanner scanner = new Scanner(System.in);
        boolean salir = false;

        while (!salir) {
            System.out.println("\n--- MENÚ DE CONTROL CLIMÁTICO ---");
            System.out.println("Ciudad actual monitorizada: " + ciudadActual);
            System.out.println("1. Cambiar ciudad");
            System.out.println("2. Salir");
            System.out.print("Seleccione opción: ");

            try {
                String input = scanner.nextLine();
                switch (input) {
                    case "1":
                        System.out.print("Introduce el nombre de la nueva ciudad (ej: Madrid,ES o Oslo): ");
                        String nuevaCiudad = scanner.nextLine().trim();
                        if (!nuevaCiudad.isEmpty()) {
                            ciudadActual = nuevaCiudad;
                            System.out.println(">>> CIUDAD CAMBIADA A: " + ciudadActual);
                            System.out.println("(El próximo chequeo automático usará la nueva localización)");
                        }
                        break;
                    case "2":
                        System.out.println("Cerrando Weather Control...");
                        salir = true;
                        timer.cancel();
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                System.out.println("Error en entrada: " + e.getMessage());
            }
            
            try { 
            	Thread.sleep(500); 
            } 
            catch (InterruptedException e) {
            	
            }
        }
        
        scanner.close();
        System.exit(0);
    }

    private static void checkWeather() {
        String ciudadObjetivo = ciudadActual; 
        
        try {
            String ciudadEncoded = ciudadObjetivo.replace(" ", "%20");
            
            String urlString = "https://api.openweathermap.org/data/2.5/weather?q=" + ciudadEncoded + "&appid=" + API_KEY;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);

            int status = conn.getResponseCode();
            if (status != 200) {
                System.err.println("[EV_W] Error consultando clima para '" + ciudadObjetivo + "' (Código: " + status + "). Verifique el nombre.");
                return;
            }

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            String json = result.toString();
            double tempKelvin = extractTempFromJSON(json);
            double tempCelsius = tempKelvin - 273.15;
            
            System.out.printf("[AUTO] %s -> %.2fºC. ", ciudadObjetivo, tempCelsius);

            boolean alertaFrio = tempKelvin < UMBRAL_FREEZE_KELVIN;
            
            if (alertaFrio) {
                System.out.print("¡ALERTA BAJA TEMPERATURA! -> Notificando a Central.\n");
            } else {
                System.out.print("Temperatura Operativa -> Enviando OK.\n");
            }

            notifyCentral(ciudadObjetivo, tempCelsius, alertaFrio);

        } catch (Exception e) {
            System.err.println("[EV_W] Error en ciclo de clima: " + e.getMessage());
        }
    }

    private static void notifyCentral(String ciudad, double tempCelsius, boolean alerta) {
        try {
            URL url = new URL(CENTRAL_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000); 

            String jsonInputString = String.format(java.util.Locale.US, 
                "{\"ciudad\": \"%s\", \"temperatura\": %.2f, \"alerta\": %b}", 
                ciudad, tempCelsius, alerta
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush(); 
            }
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();

        } 
        catch (java.net.SocketTimeoutException e) {
            System.err.println(" -> [TIMEOUT] Central tarda demasiado en responder. Siguiendo...");
        } 
        catch (Exception e) {
            System.err.println(" -> No se pudo contactar con Central: " + e.getMessage());
        }
    }

    private static double extractTempFromJSON(String json) {
        try {
            String search = "\"temp\":";
            int index = json.indexOf(search);
            if (index != -1) {
                int start = index + search.length();
                int endComma = json.indexOf(",", start);
                int endBracket = json.indexOf("}", start);
                
                int end = endComma;
                if (end == -1 || (endBracket != -1 && endBracket < endComma)) {
                    end = endBracket;
                }
                
                String tempStr = json.substring(start, end);
                return Double.parseDouble(tempStr);
            }
        } 
        catch (Exception e) {
        	
        }
        return 300.0;
    }
}