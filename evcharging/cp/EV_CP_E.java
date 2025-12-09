package p2.evcharging.cp;

import p2.evcharging.cp.network.MonitorServer;

import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.time.Duration;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import p2.db.DBManager;

public class EV_CP_E {
	private ChargingPoint cp;
	private MonitorServer monitor;
	private boolean funcionamiento;
	private boolean registrado=false;
	private Scanner scanner;
	private String host;
	private int puerto;
	private String dirKafka;
	private Thread hilo; 
	private Thread hiloEspera; 
	private KafkaConsumer<String, String> consumidor;
	private volatile boolean procesandoAveria = false;
	
	public static void main(String[] args) {
		System.setProperty("org.slf4j.simpleLogger.log.org.slf4j", "OFF");
		//para que no aparezcan los mensajes de kafka en la central 
    	System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka", "ERROR");
    	System.setProperty("org.slf4j.simpleLogger.log.kafka", "ERROR");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.common", "WARN");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients.network", "ERROR");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients.consumer", "ERROR");
    	System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka.clients.consumer.internals", "ERROR");
    	java.util.logging.Logger.getLogger("org.apache.kafka").setLevel(java.util.logging.Level.SEVERE);
		
		if (args.length < 6) {
            System.out.println("Uso: java EV_CP_E <cp_id> <ubicacion> <precio_kwh> <host:port> <dirKafka> <puerto_monitor>");
            //System.out.println("Ej: java EV_CP_E CP001 \"Calle Principal 123\" 0.15 localhost:9090 localhost:9092");
            return;
        }
		
		String cpId=args[0];
		String ubicacion=args[1];
		double precioKwh = Double.parseDouble(args[2]);
		String[] centralArgs = args[3].split(":");
		String dirKafka=args[4];
		int puertoMonitor=Integer.parseInt(args[5]);	
		
		String host=centralArgs[0];
		int puerto= Integer.parseInt(centralArgs[1]);
		
		EV_CP_E engine = new EV_CP_E();
		engine.iniciar(cpId, ubicacion, precioKwh, host, puerto, dirKafka, puertoMonitor);
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
	}
	
	public String leerDatos(Socket sock) {
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
	
	  public void iniciar(String cpId, String ubicacion, double precioKwh, String host, int puerto, String dirKafka, int puertoMonitor) {
		  try {
			  this.host=host;
			  this.puerto=puerto;
			  this.dirKafka=dirKafka;
			  this.scanner= new Scanner(System.in);
			  
			  this.cp=new ChargingPoint(cpId, ubicacion, precioKwh);
			  
			  this.monitor= new MonitorServer(cp, puertoMonitor);
			  
			  Thread hiloMonitor = new Thread(() -> monitor.iniciar());
			  hiloMonitor.start();
			  
			  iniciarEsperaMonitor(puertoMonitor);
			  
			  iniciarConsumidorCentral();

			  this.funcionamiento=true;
			  ejecutarBuclePrincipal();
		  }
		  catch(Exception e) {
			  System.err.println("Error en el inicio del engine: " + e.getMessage());
		  }
		  finally {
			  detener();
		  }
	  }
		
	  
	  
		public void registrarEnCentral() {
			if(!registrado) {
				try {
					boolean registro=cp.registroEnCentral(dirKafka);
					if(registro) {
						registrado=true;
					}
					else {
						System.out.println("Imposible conectar con la CENTRAL");
					}
				}
				catch(Exception e) {
					System.out.println("Imposible conectar con la CENTRAL");
				}
				
			}
		}
		
		private void iniciarEsperaMonitor(int puertoMonitor) {
			hiloEspera= new Thread(() -> {
				try(ServerSocket espera = new ServerSocket(puertoMonitor + 1000)){
					while(funcionamiento && !Thread.currentThread().isInterrupted()) {
						try {
							Socket s=espera.accept();
							verificarMonitorActivo(s);
						}
						catch(IOException e) {
							if(funcionamiento && !Thread.currentThread().isInterrupted()) {
								 System.err.println("Error aceptando conexión del monitor: " + e.getMessage());
							}
						}
					}

				}
				catch(IOException e) {
					if(funcionamiento && !Thread.currentThread().isInterrupted()) {
						System.err.println("Error esperando al monitor: " + e.getMessage());
					}

				}
				
			});
			hiloEspera.start();
		}

	private void verificarMonitorActivo(Socket s) {
		Thread monitorActivo=new Thread(() -> {
			try {
				s.setSoTimeout(5000);
				boolean activo=false;
				boolean primera=true;
				
				while(funcionamiento && !s.isClosed()) {
					try {
						String mensaje=leerDatos(s);
						if(mensaje==null) {
							if(activo && !procesandoAveria) {
								System.out.println("Monitor desconectado");
								marcarCPDesconectado();
							}
							
		                    break;
						}
						if("MONITOR_ACTIVO".equals(mensaje)) {
							activo=true;
							if(primera) {
								System.out.println("Monitor iniciado");
								registrarEnCentral();
								primera=false;
							}
							escribirDatos(s,"MONITOR_ACTIVO_ACK");
						}
						else if("Comprobar_Funciona".equals(mensaje)) {
					        escribirDatos(s, "Funciona_OK"); 
					    }
						Thread.sleep(2000);
					}
					catch(InterruptedException e) {
						if(activo && !procesandoAveria) {
							marcarCPDesconectado();
						}
	                    break;
					}
				}
				try {
		            s.close();
		        } 
				catch (IOException e) {
		            
		        }
			}
			catch(Exception e) {
				marcarCPDesconectado();
			}
		});
		monitorActivo.start();
	}
	
	private void marcarCPDesconectado() {
	    if (cp != null) {
	        if (cp.getEstado() == CPState.SUMINISTRANDO) {
	            cp.finalizarSuministro();
	        }
	        registrado = false;
	        actualizarEstadoEnBD();
	    }
	}

	private void actualizarEstadoEnBD() {
	    Connection conn = null;
	    PreparedStatement ps = null;
	    
	    try {
	    	//mismo caso que en resetearCPenBD, me ha tocado hacer la conexion manual a la BD y luego gestionar
	    	//la desconexion en el finally
	        String url = "jdbc:mysql://localhost:3306/ev_charging_system?useSSL=false&serverTimezone=Europe/Madrid";
	        conn = DriverManager.getConnection(url, "evuser", "evpass");
	        
	        ps = conn.prepareStatement(
	            "UPDATE charging_point SET estado='DESCONECTADO', registrado_central=FALSE WHERE id=?"
	        );
	        ps.setString(1, cp.getId());
	        ps.executeUpdate();
	        
	    } 
	    catch (SQLException e) {
	        System.err.println("[DB] Error actualizando BD: " + e.getMessage());  
	    } 
	    finally {
	        if (ps != null) {
	            try { 
	            	ps.close(); 
	            } 
	            catch (SQLException e) {
	            	
	            }
	        }
	        if (conn != null) {
	            try {
	            	conn.close(); 
	            } 
	            catch (SQLException e) {
	            	
	            }
	        }
	    }
	}

	private void iniciarConsumidorCentral() {
		hilo=new Thread (() -> {
			Properties propiedades = new Properties();
			propiedades.put("bootstrap.servers", dirKafka);
			propiedades.put("group.id", "engine-" + cp.getId());
			propiedades.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
			propiedades.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
			
			try {
				this.consumidor=new KafkaConsumer<>(propiedades);
				consumidor.subscribe(Arrays.asList("comandos-cp"));
				
				while(funcionamiento) {
					ConsumerRecords<String, String> records = consumidor.poll(Duration.ofMillis(100));
					 for (ConsumerRecord<String, String> record : records) {
		                    if (cp.getId().equals(record.key())) {
		                        procesarComandoCentral(record.value());
		                    }
					 }
				}
						
			}
			catch(Exception e) {
				System.err.println("Error en consumidor de Central: " + e.getMessage());
				System.out.println("Imposible conectar con la CENTRAL");
			}
			finally {
				if(consumidor!=null) {
					try {
						consumidor.close();
					}
					catch(Exception e) {
						
					}
				}
			}
		});
		hilo.start(); 
		
	}

	private void procesarComandoCentral(String comando) {
		String[] partes=comando.split("\\|");
		if(partes.length==0) {
			return;
		}
		
		String tipo= partes[0];
		
		switch(tipo) {
			case "Autorizacion_Solicitud":
				if(partes.length>4) {
					String driverId=partes[1];
					String sesionId=partes[2];
					String cpId=partes[3];
					procesarAutorizacionEngine(driverId, sesionId, cpId);
				}
				break;
			
			case "Inicio_Suministro":
				if(partes.length >=3) {
					String conductorId = partes[1];
	                String sesionId = partes[2];
	                iniciarSuministroAutomatico(conductorId, sesionId);
				}
				break;
				
			case "Parar":
				if (cp.getEstado() == CPState.SUMINISTRANDO) {
					System.out.println("Finalizando suministro...");
	                cp.finalizarSuministro();
	            }
	            cp.parar();
	            break;
	            
			case "Parada_Emergencia":
				if(cp.getEstado() == CPState.SUMINISTRANDO) {
					System.out.println("Finalizando suministro...");
					cp.finalizarSuministro();
				}
				cp.parar();
				break;
				
			case "Reanudar":
				cp.activar();
				break;
				
			default:
	            System.out.println("Comando no reconocido: " + tipo);

		}	
		
	}

	private void iniciarSuministroAutomatico(String conductorId, String sesionId) {
		boolean exito=cp.iniciarSuministroAutorizado(conductorId, sesionId);
		
		if(exito) {
			System.out.println("Suministro automático iniciado para: " + conductorId);
		}
		else {
			System.out.println("No se pudo iniciar el suministro automático");
		}
		
	}

	private void procesarAutorizacionEngine(String driverId, String sesionId, String cpId) {
		if(!registrado) {
			if(cp.getConector() != null) {
	            cp.getConector().enviarAutorizacion(sesionId, driverId, false);
	        }
	        return;
		}
		if(cp.getEstado() ==CPState.ACTIVADO && cp.getFunciona()) {
			//aceptada
			if(cp.getConector() !=null) {
				cp.getConector().enviarAutorizacion(sesionId, cpId, true);
			}
			 System.out.println("Autorización aceptada para driver: " + driverId);
		}
		//denegada
		else {
			if(cp.getConector() != null) {
				cp.getConector().enviarAutorizacion(sesionId, driverId, false);
				System.out.println("Autorización denegada para driver: " + driverId);
			}
		}
		
	}

	private void ejecutarBuclePrincipal() {
		while(funcionamiento) {
			mostrarMenu();
			int opcion= leerOpcion();
			procesarOpcion(opcion);
		}
	}
	
	private void mostrarMenu() {
		 System.out.println("\n--- MENÚ PRINCIPAL ---");
	        System.out.println("1.  Iniciar suministro manual");
	        System.out.println("2.  Finalizar suministro");
	        System.out.println("3.  Activar CP");
	        System.out.println("4.  Parar CP");
	        System.out.println("5.  Simular avería");
	        System.out.println("6.  Reparar avería");
	        System.out.println("7.  Estado completo");
	        System.out.println("8.  Salir");
	        
	        if(!registrado) {
	        	System.out.println("Monitor desconectado, solo disponible: 5, 7, 8");
	        }
	        
	        System.out.print("Seleccione opción: ");
	}
	
	private int leerOpcion() {
	    try {
	        return scanner.nextInt();
	    } 
	    catch (Exception e) {
	        scanner.nextLine();
	        System.out.println("Opcion incorrecta. Introduce un número del 1 al 10.");
	        return -1;
	    }
	}
	
	private void procesarOpcion(int opcion) {
		if(!registrado && opcion!=5 && opcion!=7 && opcion!=8) {
			System.out.println("No disponible, no esta conectado el monitor");
			return;
		}
        switch (opcion) {
            case 1:
                iniciarSuministroManual();
                break;
            case 2:
                finalizarSuministro();
                break;
            case 3:
                activarCP();
                break;
            case 4:
                pararCP();
                break;
            case 5:
                simularAveria();
                break;
            case 6:
                repararAveria();
                break;
            case 7:
                mostrarEstadoCompleto();
                break;
            case 8:
                System.out.println("Saliendo...");
                funcionamiento = false;
                break;
            default:
                System.out.println("Opción incorrecta. Introduce un número del 1 al 10.");
        }
    }
	
	private void iniciarSuministroManual() {
		if(!registrado) {
			System.out.println("No se puede iniciar suministro, monitor desconectado");
			return;
		}
		
		System.out.println("Introduce el ID del conductor: ");
		String conductorId=scanner.next();
		
		boolean exito= cp.iniciarSuministroManual(conductorId);
		
		if(exito) {
			System.out.println("Suministro manual inciado para: " + conductorId);
			System.out.println("Recarga en progreso...");
		    System.out.println("Pulse 2 para finalizar el suministro");
		}
		else {
			//System.out.println("No se ha podido iniciar el suministro manual");
		}
	}
	
	private void finalizarSuministro() {
		if (cp.getEstado() != CPState.SUMINISTRANDO) {
            System.out.println("No hay suministros trabajando en este momento");
            return;
        }
		
		System.out.println("Finalizando suministro para conductor: " + cp.getConductorActual());
		cp.finalizarSuministro();
		System.out.println("Suministro finalizado");
		
	}
	
	private void activarCP() {
		System.out.println("Activando CP con id: " + cp.getId());
		cp.activar();
		//System.out.println("CP activado");
	}
	
	private void pararCP() {
		System.out.println("Parando CP con id: " + cp.getId());
		cp.parar();
		//System.out.println("CP fuera de servicio");
	}
	
	private void simularAveria() {
		if(cp.getFunciona()) {
			System.out.println("Fallo en el CP " + cp.getId());
			procesandoAveria=true;
			try {
				cp.setFunciona(false);
				System.out.println("CP en estado de fallo, se ha notificado la averia a la Central");
			}
			finally {
				procesandoAveria=false;
			}
			
		}
		else {
			System.out.println("El CP ya esta averiado: " +cp.getEstado());
		}
	}
	

	private void repararAveria() {
		if(cp.getEstado()==CPState.AVERIADO || !cp.getFunciona()) {
			System.out.println("Reparando averia en CP " + cp.getId());
			procesandoAveria=true;
			try {
				cp.setFunciona(true);
				System.out.println("Averia reparada, se ha notificado la recuperación a la Central");
			}
			finally {
				procesandoAveria=false;
			}
			
		}
		else {
			System.out.println("No hay averia en el CP: " +cp.getEstado());
		}
	}


	private void mostrarEstadoCompleto() {
		cp.imprimirInfoCP();
	}

	
	private void detener() {
		funcionamiento =false;
		try {
			if(consumidor!=null) {
				consumidor.wakeup();
			}
			
			if(hilo !=null && hilo.isAlive()) {
				hilo.interrupt();
				hilo.join(1000);
			}
			
			if(hiloEspera !=null && hiloEspera.isAlive()) {
				hiloEspera.interrupt();
			}
			
			Thread.sleep(500);
			
			if(scanner != null) {
				scanner.close();
			}
			
			if(monitor != null) {
				monitor.detener();
			}
			
			if(cp !=null && cp.getConector() != null) {
				cp.getConector().cerrarConexiones();
			}
						
			resetearCPenBD();
			
			System.out.println("Engine detenido");
		}
		catch(Exception e) {
			System.out.println("Error, no se ha podido detener el engine: " + e.getMessage());
		}
	}
	
	private void resetearCPenBD() {
	    if (cp != null) {
	        Connection conn = null;
	        PreparedStatement ps = null;
	        
	        try {
	            //se tiene que crear la conexion independiente ya que sino no va con el DBManager, antes usaba la misma 
	        	//conexion para todos y hacia que saltaran excepciones SQL
	            String url = "jdbc:mysql://localhost:3306/ev_charging_system?useSSL=false&serverTimezone=Europe/Madrid";
	            conn = DriverManager.getConnection(url, "evuser", "evpass");
	            
	            ps = conn.prepareStatement(
	                "UPDATE charging_point SET estado='DESCONECTADO', funciona=TRUE, registrado_central=FALSE, conductor_actual=NULL, consumo_actual=0.0, importe_actual=0.0 WHERE id=?"
	            );
	            ps.setString(1, cp.getId());
	            ps.executeUpdate();
	            
	        } 
	        catch (SQLException e) {
	            System.err.println("[DB] Error resetando CP en BD: " + e.getMessage());
	        } 
	        finally {
	            if (ps != null) {
	                try { 
	                	ps.close(); 
	                } 
	                catch (SQLException e) {
	                	
	                }
	            }
	            if (conn != null) {
	                try { 
	                	conn.close(); 
	                } 
	                catch (SQLException e) { 
	                		
	                }
	            }
	        }
	    }
	}

	public ChargingPoint getChargingPoint() {
        return cp;
    }

    public MonitorServer getMonitorService() {
        return monitor;
    }

    public boolean getFuncionamiento() {
        return funcionamiento;
    }
    
    public boolean getRegistrado() {
    	return registrado;
    }
}
