package p2.central;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.sql.*;
import p2.db.DBManager;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import java.time.Duration;


public class HiloServidor extends Thread {
	private KafkaProducer<String, String> productor;
    private KafkaConsumer<String, String> consumidor;
    private boolean ejecucion;
		
	public HiloServidor(KafkaProducer<String, String> productor, KafkaConsumer<String, String> consumidor) {
		 this.productor=productor;
		 this.consumidor=consumidor;
		 this.ejecucion=false;
	}

	@Override
	public void run() {
		this.ejecucion=true;
		procesarMensajesKafka();
	}

	 private void procesarMensajesKafka() {
		 while(ejecucion) {
			 try {
				 //Libreria para que el consumidor reciba los mensajes desde kafka
				 ConsumerRecords<String, String> records = consumidor.poll(Duration.ofMillis(100));
				 //cada mensaje que llega se procesa en un hilo separado al resto
				 records.forEach(record -> {
					 new Thread(() -> procesarMensaje(record.topic(), record.key(), record.value())).start();
				 });
			 }
			 catch(Exception e) {
				 if(ejecucion) {
					 System.err.println("Error procesando los mensajes de Kafka desde los hilos: " + e.getMessage());
				 }
			 }
		 }
	 }
	
	private void procesarMensaje(String tema, String cpId, String mensaje) {
		//System.out.println("Se ha recibido - Tema: " + tema + ", CP: " + cpId + ", Mensaje: " + mensaje);
		
		try {
			switch (tema) {
	            case "cp-registro":
	                procesarRegistro(cpId, mensaje);
	                break;
	            case "cp-estado":
	                procesarActualizacionEstado(cpId, mensaje);
	                break;
	            case "cp-autorizacion":
	                procesarAutorizacion(cpId, mensaje);
	                break;
	            case "actualizacion-recarga":
	                procesarActualizacionRecarga(cpId, mensaje);
	                break;
	            case "fallo-cp":
	                procesarAveria(cpId, mensaje);
	                break;
	            case "recuperacion-cp":
	                procesarRecuperacion(cpId, mensaje);
	                break;
	            case "monitor-registro":
	            	procesarRegistroMonitor(cpId, mensaje);
	            	break;
	            case "monitor-estado":
	            	procesarEstadoMonitor(cpId, mensaje);
	            	break;
	            case "ticket":
	            	procesarTicket(cpId, mensaje);
	            	break;
	            case "driver-solicitud":
	            	procesarSolicitudDriver(cpId, mensaje);
	            	break;
	            case "monitor-averias":
	            	procesarAveriaMonitor(cpId, mensaje);
	                break;
	            default:
	                System.out.println("Tema no reconocido: " + tema);
			}
		}
		catch(Exception e) {
			System.err.println("Error procesando el mensaje: " + e.getMessage());
		}
	}

	private void procesarEstadoMonitor(String cpId, String mensaje) {
		if(mensaje.startsWith("Monitor_Desconectado")) {
	        System.out.println("Monitor desconectado para CP: " + cpId);
	        
	        actualizarEstadoCP(cpId, "DESCONECTADO", true);

	        registrarEvento(cpId, "MONITOR_DESCONECTADO", "Monitor desconectado - CP marcado como DESCONECTADO");
	        
	        String confirmacion = "Monitor_Desconectado_ACK|" + cpId;
	        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
	    }
	}

	private void procesarAveriaMonitor(String cpId, String mensaje) {
		String confirmacion = "Averia_Monitor_ACK|" + cpId;
	    productor.send(new ProducerRecord<>("central-to-monitor", cpId, confirmacion));
	    
	    String evento = "Averia_Monitor|" + cpId;
	    productor.send(new ProducerRecord<>("sistema-eventos", cpId, evento));
	    
	    registrarEvento(cpId, "AVERIA_MONITOR", "Avería reportada por Monitor: " + mensaje);
	    actualizarEstadoCP(cpId, "AVERIADO", false);
	    
	}

	private void procesarSolicitudDriver(String driverId, String mensaje) {
	    System.out.println("[CENTRAL] Solicitud recibida de driver: " + driverId + " -> " + mensaje);
		
	    String[] partes = mensaje.split("\\|");
	    if (partes.length < 3) {
	        System.err.println("[CENTRAL] Formato inválido en driver-solicitud");
	        return;
	    }
	
	    String cpSolicitado = partes[2];
	
	    String cpDisponible = null;
	
	    try (Connection conn = DBManager.getConnection();
	         PreparedStatement ps = conn.prepareStatement(
	                 "SELECT id FROM charging_point " +
	                         "WHERE id = ? AND estado='ACTIVADO' AND funciona=TRUE AND registrado_central=TRUE")) {
							
	        ps.setString(1, cpSolicitado);
	        ResultSet rs = ps.executeQuery();
							
	        if (rs.next()) {
	            cpDisponible = rs.getString("id");
	        }
	    } catch (SQLException e) {
	        System.err.println("[DB] Error verificando CP: " + e.getMessage());
	    }
	
	    if (cpDisponible == null) {
	        productor.send(new ProducerRecord<>("driver-autorizacion-" + driverId,
	                driverId, "Denegado|" + cpSolicitado));
	        return;
	    }
	
	    String sesionId = "SES-" + System.currentTimeMillis();
	
	    try (Connection conn = DBManager.getConnection();
	         PreparedStatement ps = conn.prepareStatement(
	                 "INSERT INTO charging_session (session_id, cp_id, conductor_id, estado) VALUES (?,?,?, 'EN_CURSO')")) {
					
	        ps.setString(1, sesionId);
	        ps.setString(2, cpDisponible);
	        ps.setString(3, driverId);
	        ps.executeUpdate();
	    } catch (SQLException e) {
	        System.err.println("[DB] Error creando sesión: " + e.getMessage());
	    }
	
	    String comando = "Inicio_Suministro|" + driverId + "|" + sesionId;
	    productor.send(new ProducerRecord<>("comandos-cp", cpDisponible, comando));
	
	    productor.send(new ProducerRecord<>("driver-autorizacion-" + driverId,
	            driverId, "Autorizado|" + cpDisponible + "|" + sesionId));
	
	    CentralDashboard.refrescarDesdeCentral();
	}	
	

	private void procesarRegistroMonitor(String cpId, String mensaje) {
		System.out.println("Monitor registrado para CP: " + cpId);
	    
	    String confirmacion = "Monitor_Registro_OK|" + cpId;
	    productor.send(new ProducerRecord<>("central-to-monitor", cpId, confirmacion));
	    registrarEvento(cpId, "REGISTRO_MONITOR", "Monitor registrado: " + mensaje);
	}

	private void procesarRecuperacion(String cpId, String mensaje) {
		if(mensaje.startsWith("Recuperacion_Reporte")) {
			System.out.println("Reporte del monitor, recuperacion en CP: " + cpId);
		}
		else {
			System.out.println("Recuperacion en CP: " + cpId);
		}
		
		String confirmacion = "Recuperacion_ACK|" + cpId;
        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
        
        String confirmacion2= "Recuperacion|" + cpId;
        productor.send(new ProducerRecord<>("sistema-eventos", cpId, confirmacion2));
        
        registrarEvento(cpId, "RECUPERACION", "CP recuperado: " + mensaje);
        
        String estadoActual = obtenerEstadoActualCP(cpId);
        
        if ("PARADO".equals(estadoActual)) {
            actualizarEstadoCP(cpId, "PARADO", true);
        } 
        else {
            actualizarEstadoCP(cpId, "ACTIVADO", true);
        }
	}
	
	private String obtenerEstadoActualCP(String cpId) {
	    try (Connection conn = DBManager.getConnection();
	         PreparedStatement ps = conn.prepareStatement(
	                 "SELECT estado FROM charging_point WHERE id=?")) {
	        ps.setString(1, cpId);
	        ResultSet rs = ps.executeQuery();
	        
	        if (rs.next()) {
	            return rs.getString("estado");
	        }
	    } catch (SQLException e) {
	        System.err.println("[DB] Error consultando estado CP: " + e.getMessage());
	    }
	    return "ACTIVADO";
	}

	private void procesarAveria(String cpId, String mensaje) {
		System.out.println("Averia en CP: " + cpId);
		
		String confirmacion = "Averia_ACK|" + cpId;
        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
        
        String confirmacion2= "Averia|" + cpId;
        productor.send(new ProducerRecord<>("sistema-eventos", cpId, confirmacion2));
        
        registrarEvento(cpId, "AVERIA", "Avería detectada: " + mensaje);
        actualizarEstadoCP(cpId, "AVERIADO", false);

	}

	private void procesarActualizacionRecarga(String cpId, String mensaje) {
		String[] partes = mensaje.split("\\|");
        String consumo = partes[3];
        String importe = partes[2];
        
        System.out.printf("CP: %s | Consumo: %s kW | Importe: %s €%n", cpId, consumo, importe);
        
        String confirmacion = "Consumo_OK|" + cpId;
        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
        registrarEvento(cpId, "CONSUMO_UPDATE",
                String.format("Actualización de consumo: %s kWh / %s €", consumo, importe));
	}

	private void procesarAutorizacion(String cpId, String mensaje) {
		System.out.println("Procesando autorización CP " + cpId + ": " + mensaje);
		
		if(mensaje.contains("Aceptada")) {
			String confirmacion = "Autorización_OK_ACK|" + cpId;
	        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
	        registrarEvento(cpId, "AUTORIZACION_OK", mensaje);
		}
		else {
			String confirmacion = "Autorización_DEN_ACK|" + cpId;
	        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
	        registrarEvento(cpId, "AUTORIZACION_DENEGADA", mensaje);
		}
	}

	private void procesarActualizacionEstado(String cpId, String mensaje) {
		String[] partes = mensaje.split("\\|");
        String estado = partes[2];
        String funciona = partes[3];

        System.out.println("Estado actualizado CP: " + cpId + ": " + estado + " - Funciona: " + funciona);
        
        String confirmacion = "Actualizacion_OK|" + cpId;
        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
        registrarEvento(cpId, "ACTUALIZACION_ESTADO", "Nuevo estado: " + estado);
        
        boolean funcionaBool = "Ok".equalsIgnoreCase(funciona);
        actualizarEstadoCP(cpId, estado, funcionaBool);
	}

	private void procesarRegistro(String cpId, String mensaje) {
		String[] partes = mensaje.split("\\|");
        String ubicacion = partes[2];
        String precio = partes[3];

        System.out.println("CP registrado: " + cpId + " en " + ubicacion + " - Precio: " + precio);
        
        String confirmacion = "Registro_OK|" + cpId;
        productor.send(new ProducerRecord<>("central-to-cp", cpId, confirmacion));
        registrarEvento(cpId, "REGISTRO_CP", "CP registrado en " + ubicacion);
        
        try (Connection conn = DBManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE charging_point SET registrado_central=TRUE, estado='ACTIVADO', ubicacion=?, precio_kwh=? WHERE id=?")) {
               ps.setString(1, ubicacion);
               ps.setDouble(2, Double.parseDouble(precio));
               ps.setString(3, cpId);
               ps.executeUpdate();
        } catch (SQLException e) {
        	System.err.println("[DB] Error actualizando CP: " + e.getMessage());
        }
		
	}
	
	private void procesarTicket(String cpId, String mensaje) {
	    String[] partes = mensaje.split("\\|");
	    String conductorId = partes[1];
	    String consumo = partes[2];
	    String importe = partes[3];
	
	    System.out.printf("[CENTRAL] Ticket recibido de %s → Driver %s (%s kWh / %s €)%n",
	            cpId, conductorId, consumo, importe);

	    productor.send(new ProducerRecord<>("central-to-cp", cpId, "Ticket_ACK|" + cpId));
	
	    String mensajeDriver = String.format("Ticket|%s|%s|%s", cpId, consumo, importe);
	    productor.send(new ProducerRecord<>("driver-ticket-" + conductorId, conductorId, mensajeDriver));
	
	    registrarEvento(cpId, "TICKET_ENVIADO",
	            String.format("Ticket enviado a %s: %s kWh / %s €", conductorId, consumo, importe));
	}

	public void detener() {
		ejecucion=false;
		this.interrupt();
		System.out.println("Hilo servidor detenido");
	}
	
	private void registrarEvento(String cpId, String tipo, String descripcion) {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO event_log (cp_id, tipo_evento, descripcion) VALUES (?, ?, ?)")) {
            ps.setString(1, cpId);
            ps.setString(2, tipo);
            ps.setString(3, descripcion);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error registrando evento: " + e.getMessage());
        }
    }
	
	private void actualizarEstadoCP(String cpId, String estado, boolean funciona) {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE charging_point SET estado=?, funciona=?, ultima_actualizacion=NOW() WHERE id=?")) {
            ps.setString(1, estado);
            ps.setBoolean(2, funciona);
            ps.setString(3, cpId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error actualizando estado CP: " + e.getMessage());
        }
    }
}
