package p2.central;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.Properties;
import java.util.Scanner;
import p2.db.DBManager;
import java.sql.*;

import javax.swing.SwingUtilities;

//El StringSerializer y StringDeserializer lo que hacen es la conversion de string a bytes y viceversa
//ya que kafka trabaja con bytes


public class EV_Central {
	private static KafkaProducer<String, String> productor;
    private static Scanner scanner;
    private static boolean ejecucion = true;
    
    public static void main(String[] args) {
    	//para que no aparezcan los mensajes de kafka en la central 
    	System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.kafka", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.common", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients.network", "ERROR");
    	System.setProperty("org.slf4j.simpleLogger.log.org.slf4j", "WARN");
    	java.util.logging.Logger.getLogger("org.apache.kafka").setLevel(java.util.logging.Level.SEVERE);
    	
        if (args.length < 1) {
        	System.out.println("Uso: java p2.central.EV_Central <host:puerto>");
            //System.out.println("Ejemplo: java p2.central.EV_Central localhost:9092");
            System.exit(1);
        }
        
        DBManager.connect();
        
        String dirKafka = args[0];
        System.out.println("Central iniciando...");
        System.out.println("Conectando a Kafka en: " + dirKafka);
        
        try {
        	 //Configuracion productor con Kafka
            Properties propiedadesProductor = new Properties();
            propiedadesProductor.put("bootstrap.servers", dirKafka);
            propiedadesProductor.put("key.serializer", StringSerializer.class.getName());
            propiedadesProductor.put("value.serializer", StringSerializer.class.getName());
            propiedadesProductor.put("acks", "1");
            productor = new KafkaProducer<>(propiedadesProductor);
            
            //Configuracion propiedades consumidor en Kafka
            Properties propiedadesConsumidor = new Properties();
            propiedadesConsumidor.put("bootstrap.servers", dirKafka);
            propiedadesConsumidor.put("group.id", "central-group");
            propiedadesConsumidor.put("key.deserializer", StringDeserializer.class.getName());
            propiedadesConsumidor.put("value.deserializer", StringDeserializer.class.getName());
            propiedadesConsumidor.put("auto.offset.reset", "earliest");
            KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(propiedadesConsumidor);
            
            ServidorConcurrente servidor = new ServidorConcurrente(productor, consumidor);
            servidor.iniciar();
            System.out.println("EV_Central iniciado correctamente");
            System.out.println("Escuchando mensajes de los CP...");
            
            SwingUtilities.invokeLater(() -> {
                CentralDashboard dashboard = new CentralDashboard();
                dashboard.setVisible(true);
            });
           
            
            scanner=new Scanner(System.in);
            Thread hilo=new Thread(() -> iniciarComandos());
            hilo.start();
            
            mantenerEjecucion();
        }
        catch(Exception e) {
        	System.err.println("Error iniciando la Central: " + e.getMessage());
        }
        finally {
        	detener();
        }
    }

	private static void detener() {
		ejecucion=false;
		try {
			if (scanner!=null) {
				scanner.close();
			}
			
			if(productor!=null) {
				productor.close();
			}
			System.out.println("Central detenida correctamente.");
		}
		catch(Exception e) {
			System.err.println("Error deteniendo la Central: " + e.getMessage());
		}
		
	}

	private static void iniciarComandos() {
		System.out.println("Comandos activos: P=Parar, R=Reanudar, E=Emergencia");
        
        while(ejecucion) {
        	try {
        		if(scanner.hasNext()) {
        			String tecla=scanner.next().toUpperCase();
        			System.out.print("Ingrese ID del CP (o 'ALL' para todos): ");
                    String cpId = scanner.next();
                    
                    procesarTecla(tecla, cpId);
        		}
        		
        		Thread.sleep(100);
        	}
        	catch(Exception e) {
        		System.err.println("Error procesando comando: " + e.getMessage());
        	}
        }
        
	}

	private static void procesarTecla(String tecla, String cpId) {
		String comando="";
		
		switch(tecla) {
			case "P":
				comando="Parar";
				break;
				
			case "R":
				comando="Reanudar";
				break;
				
			case "E":
				comando = "Parada_Emergencia";
				break;
				
			default:
                System.out.println("Tecla no válida. Use P, R o E");
                return;
		}
		
		try {
			ProducerRecord<String, String> record = new ProducerRecord<>("comandos-cp", cpId, comando);
            productor.send(record);
            productor.flush();
		}
		catch(Exception e) {
			System.err.println("Error enviando comando: " + e.getMessage());
		}
	}

	private static void mantenerEjecucion() {
		try {
			while(true) {
				Thread.sleep(1000);
			}
		}
		catch(Exception e) {
			System.out.println("Central interrumpida");
		}
	}
}
