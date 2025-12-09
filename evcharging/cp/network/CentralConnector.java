package p2.evcharging.cp.network;

import p2.evcharging.cp.ChargingPoint;
import java.io.*;
import java.net.Socket;
import java.util.Locale;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class CentralConnector {
	private String dirKafka;
	private ChargingPoint cp;
	private KafkaProducer<String, String> productor; 
	private boolean conectado;
	
	public CentralConnector(String dirKafka, ChargingPoint cp) {
		this.dirKafka=dirKafka;
		this.cp=cp;
		this.conectado=false;
		configurarKafka();
	}
	
	private void configurarKafka() {
		try {
			Properties props = new Properties();
			props.put("bootstrap.servers", dirKafka);
			props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("acks", "1");
            props.put("retries", 3);
            this.productor = new KafkaProducer<>(props);
			//System.out.println("Kafka configurado correctamente");
		}
		catch(Exception e) {
			System.err.println("Error configurado correctamente " + e.getMessage());
		}
	}
	
	/*public String leerDatos(Socket sock) {
		String datos = "";
		try {
			InputStream aux= sock.getInputStream();
			DataInputStream flujo = new DataInputStream(aux);
			datos=flujo.readUTF();
		}
		catch(EOFException eof) {
			return null;
		}
		
		catch (IOException e) {
			System.out.println("Error al leer datos " + e.toString());
		}
		return datos;
	}
	
	
	public void escribirDatos(Socket sock, String datos) {
		try {
			OutputStream aux= sock.getOutputStream();
			DataOutputStream flujo = new DataOutputStream(aux);
			flujo.writeUTF(datos);
		}
		catch (Exception e) {
			System.out.println("Error al escribir datos " + e.toString());
		}
	}*/
	
	
	public boolean registrarCentral() {
		try {
            
            String registroStr = String.format(Locale.US, "Registro|%s|%s|%.2f", cp.getId(), cp.getUbicacion(), cp.getPrecioKwh());
            enviarEventoKafka("cp-registro", registroStr);
            this.conectado=true;
            System.out.println("CP " + cp.getId() + " registrado en la Central");
            return true;

		}
		catch(Exception e) {
			System.err.println("Error al registar el CP en la central: " + e.getMessage());
			this.conectado=false;
			return false;
		}
		
	}
	
	public void enviarEstadoACentral() {
		String funciona;
		try {
			if(cp.getFunciona()) {
				funciona="Ok";
			}
			else {
				funciona="Ko";
			}
			
			String estadoStr=String.format("Actualizacion_Estado|%s|%s|%s", cp.getId(), cp.getEstado().name(), funciona);
			enviarEventoKafka("cp-estado", estadoStr);
			//System.out.println("Estado actualizado en la Central");
			
		}
		catch(Exception e) {
			System.err.println("Error actualizando estado: " + e.getMessage());
		}
		
	}

	public void enviarAutorizacion(String sesionId, String conductorId, boolean autorizado) {
		String mensaje;
		try {
			if(autorizado) {
				mensaje= String.format("Autorización_Aceptada|%s|%s", sesionId, conductorId);
			}
			else {
				mensaje= String.format("Autorización_Denegada|%s|%s", sesionId, conductorId);
			}
			enviarEventoKafka("cp-autorizacion", mensaje);
			System.out.println("Autorización enviada a la Central");
		}
		catch(Exception e) {
			System.err.println("Error enviando autorización: " + e.getMessage());
		}
	}


	public void enviarActualizacionConsumo(double consumoActual, double importeActual) {
		try {
			String mensaje = String.format("Actualización_Consumo|%s|%.2f|%.2f", cp.getId(), consumoActual, importeActual);
            enviarEventoKafka("actualizacion-recarga", mensaje);
            //System.out.println("Consumo actualizado en la Central");
		}
		catch(Exception e) {
			System.err.println("Error actualizando consumo: " + e.getMessage());
		}
		
	}

	public void reportarAveria() {
		try {
			String mensaje="Averia|" + cp.getId();
			enviarEventoKafka("fallo-cp", mensaje);
			//System.out.println("Avería reportada a la Central");
		}
		catch(Exception e) {
			System.err.println("Error reportando la avería: " + e.getMessage());
		}
	}

	public void reportarRecuperacion() {
		try {
			String mensaje="Recuperacion|" + cp.getId();
			enviarEventoKafka("recuperacion-cp", mensaje);
			//System.out.println("Recuperacion reportada a la Central");
		}
		catch(Exception e) {
			System.err.println("Error reportando la recuperacion: " + e.getMessage());
		}
	}
	
	public void enviarTicket(String conductorId, double consumo, double importe) {
		try {
			String mensaje = String.format("Ticket|%s|%.2f|%.2f", conductorId, consumo, importe);
			enviarEventoKafka("ticket", mensaje);
		}
		catch(Exception e) {
	        System.err.println("Error enviando ticket: " + e.getMessage());
	    }
	}
	
	private void enviarEventoKafka(String tema, String mensaje) {
		if(productor == null) {
			System.out.println("No esta disponible el productor");
			return;
		}
		
		try {
			ProducerRecord<String, String> record= new ProducerRecord<>(tema, cp.getId(), mensaje);
			productor.send(record);
			//System.out.println("Evento enviado a Kafka --> Tema: " + tema + ", Mensaje: " + mensaje);
		}
		catch(Exception e) {
			System.err.println("Error de Kafka en el envio del evento a la central: " + e.getMessage());
		}
		
	}
	
	public boolean estaConectado() {
		return conectado && productor !=null;
	}
	
	public void cerrarConexiones() {
		conectado=false;
		try {
			if(productor !=null) {
				productor.close();
			}
			System.out.println("Conexiones cerradas correctamente");
		}
		catch(Exception e) {
			System.err.println("Error cerrando conexiones: " +e.getMessage());
		}
	}
}
