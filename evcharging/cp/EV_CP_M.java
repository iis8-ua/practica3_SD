package p2.evcharging.cp;

import p2.evcharging.cp.network.CentralConnector;
import java.io.*;
import java.net.Socket;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class EV_CP_M {
	private String cpId;
    private String hostEngine;
    private int puertoEngine;
    private String dirKafka;
    private boolean ejecucion;
    private boolean inicio;
    private boolean engine=false;

    private Thread hilo;
    
    
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
    	
        if (args.length < 5) {
        	System.out.println("Uso: java EV_CP_M <host_engine> <puerto_engine> <cp_id> <dirKafka> <puerto_monitor>");
            //System.out.println("Ej: java EV_CP_M localhost 8080 CP001 localhost:9092");
            return;
        }

        String hostEngine = args[0];
        int puertoEngine = Integer.parseInt(args[1]);
        String cpId = args[2];
        String dirKafka = args[3];
        int puertoMonitor=Integer.parseInt(args[4]);

        EV_CP_M monitor = new EV_CP_M();
        monitor.iniciar(hostEngine, puertoEngine, cpId, dirKafka, puertoMonitor);
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
    
    public void iniciar(String hostEngine, int puertoEngine, String cpId, String dirKafka, int puertoMonitor) {
    	try {
    		this.hostEngine = hostEngine;
            this.puertoEngine = puertoEngine;
            this.cpId = cpId;
            this.dirKafka = dirKafka;
            this.ejecucion = true;
            
            if (!conectarCentral()) {
            	System.err.println("No se ha podido establecer conexion con la central. Saliendo...");
                return;
            }
            
            System.out.println("Monitor iniciado para el CP: " + cpId);
            System.out.println("Conectado a Central en: " + dirKafka);
            
            conectarAEngine();
            
            verificarEstadoInicial();
            verificarEstadoAuto();
            mantenerEjecucion();
            
    	}
    	catch(Exception e) {
    		System.err.println("Error en el inicio del monitor: " + e.getMessage());
    	}
    	finally {
    		detener();
    	}
    }
    

	private void conectarAEngine() {
		try {
			Socket s=new Socket(hostEngine, puertoEngine+1000);
			engine=true;
			verificarMonitorActivoEngine(s);
		}
		catch (IOException e) {
	        engine=false;
	    }
	}

	private void verificarMonitorActivoEngine(Socket s) {
		Thread monitorActivo=new Thread(() -> {
			try {
				s.setSoTimeout(3000);
				
				while(ejecucion && !s.isClosed() && engine) {
					try {
						escribirDatos(s, "MONITOR_ACTIVO");
						String respuesta=leerDatos(s);
						
						if(respuesta==null || !respuesta.contains("MONITOR_ACTIVO_ACK")) {
							engine=false;
							break;
						}
						Thread.sleep(2000);
						
					}
					catch(InterruptedException e) {
						engine=false;
	                    break;
					}
				}
				if(!s.isClosed()) {
					s.close();
				}
				engine=false;
			}
			catch(Exception e) {
				engine=false;
			}
		});
		monitorActivo.start();
	}

	private void verificarEstadoInicial() {
		boolean estadoInicial=verificarEstadoEngine();
		if(!estadoInicial) {
			System.err.println("Engine no está disponible en " + hostEngine + ":" + puertoEngine);
			reportarAveriaInicial();
			inicio=false;
		}
		else {
			System.out.println("Conectado al Engine en: " + hostEngine + ":" + puertoEngine);
			if(!engine) {
				conectarAEngine();
				engine=true;
			}
			inicio=true;
		}
	}

	private void reportarAveriaInicial() {
		try {
			Properties propiedades= new Properties();
			propiedades.put("bootstrap.servers", dirKafka);
			propiedades.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			propiedades.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		    propiedades.put("acks", "1");
		    KafkaProducer<String, String> productor = new KafkaProducer<>(propiedades);
		    
		    String mensaje = "Averia_Inicial|" + cpId + "|Engine_no_disponible";
	        ProducerRecord<String, String> record = new ProducerRecord<>("monitor-averias", cpId, mensaje);
	        productor.send(record);
	        System.out.println("Avería inicial reportada a central, engine no disponible al iniciar");
	        productor.close();
		}
		catch(Exception e) {
			System.err.println("Error reportando la avería inicial: " + e.getMessage());
		}
	}

	private void mantenerEjecucion() {
		try {
			while (ejecucion && hilo != null && hilo.isAlive()) {
                Thread.sleep(1000);
            }
        } 
		catch (InterruptedException e) {
            System.out.println("Monitor interrumpido");
        }
		
	}

	private void detener() {
		ejecucion=false;
		try {
			
			if(hilo !=null) {
				hilo.interrupt();
				hilo.join(2000);
			}
			System.out.println("Monitor detenido");
		}
		catch(Exception e) {
			System.err.println("Error deteniendo el monitor: " + e.getMessage());
		}
	}

	private boolean verificarEstadoEngine() {
		try {
			Socket s= new Socket(hostEngine, puertoEngine+1000);
			s.setSoTimeout(3000); 
			escribirDatos(s, "Comprobar_Funciona");
			
			String respuesta= leerDatos(s);
			s.close();
			if ("Funciona_OK".equals(respuesta)) {
				return true;
			}
			else {
				return false;
			}
		}
		catch(java.net.ConnectException e) {
			//cuando el engine cae
			//System.err.println("Engine caido en " + hostEngine + ":" + puertoEngine);
	        return false;
		}
		catch(java.net.SocketTimeoutException e) {
			//cuando no responde
			System.err.println("Engine no responde, timeout en la conexión");
	        return false;
		}
		catch(IOException e) {
			 System.err.println("Error verificando estado del Engine: " + e.getMessage());
			 return false;
		}
	}
	
	

	private boolean conectarCentral() {
		try {
			Properties propiedades= new Properties();
			propiedades.put("bootstrap.servers", dirKafka);
			propiedades.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			propiedades.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		    propiedades.put("acks", "1");
		    KafkaProducer<String, String> productor = new KafkaProducer<>(propiedades);
		    
		    String registroStr = String.format("Monitor_Registro|%s", cpId);
		    ProducerRecord<String, String> record = new ProducerRecord<>("monitor-registro", cpId, registroStr);
		    productor.send(record);
		    System.out.println("Monitor registrado en la Central para CP: " + cpId);
	        productor.close();
	        return true;
		}
		catch(Exception e) {
			 System.err.println("Error en la conexion con la Central: " + e.getMessage());
			 System.out.println("Imposible conectar con la CENTRAL");
			 return false;
		}
	}
	
	private void reportarAveria() {
		try {
			Properties propiedades = new Properties();
			propiedades.put("bootstrap.servers", dirKafka);
			propiedades.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			propiedades.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
	        KafkaProducer<String, String> productor = new KafkaProducer<>(propiedades);
	        
	        String mensaje="Averia_Reporte|" +cpId;
	        ProducerRecord<String, String> record = new ProducerRecord<>("monitor-averias", cpId, mensaje);
	        productor.send(record);
	        System.out.println("Avería reportada a Central");
	        productor.close();
		}
		catch(Exception e) {
	        System.err.println("Error reportando la avería: " + e.getMessage());

		}
	}
	
	private void reportarRecuperacion() {
		try {
			Properties propiedades = new Properties();
			propiedades.put("bootstrap.servers", dirKafka);
			propiedades.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			propiedades.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
	        KafkaProducer<String, String> productor = new KafkaProducer<>(propiedades);
	        
	        String mensaje="Recuperacion_Reporte|" +cpId;
	        ProducerRecord<String, String> record = new ProducerRecord<>("monitor-averias", cpId, mensaje);
	        productor.send(record);
	        System.out.println("Recuperacion reportada a Central");
	        productor.close();
		}
		catch(Exception e) {
	        System.err.println("Error reportando la recuperacion: " + e.getMessage());

		}
	}
	
	//se usa una funcion lambda para que se haga cada vez que se cree un nuevo hilo
	//Con un hilo lo que hace es verificar el estado del engine cada 2 segundos y se detiene al pulsar enter, se puede seguir usando el menu mientras se ejecuta
	private void verificarEstadoAuto() {		
		hilo = new Thread(new Runnable() {
			private boolean ultimo=false;
			private boolean conectado=inicio;
			private boolean primera=true;
			@Override
			public void run() {
				while(!Thread.currentThread().isInterrupted() && ejecucion) {
					try {
						boolean estado=verificarEstadoEngine();
						
						if(primera) {
							primera=false;
							conectado=estado;
							ultimo=estado;
							if(estado) {
								System.out.println("Engine conectado - " + java.time.LocalTime.now());
								if(!engine) {
									conectarAEngine();
									engine=true;
								}
							}
							else {
								System.out.println("Engine desconectado - " + java.time.LocalTime.now());
							}
						}
						
						else if (!estado && ultimo && conectado) {
							System.out.println("Engine Averiado - " + java.time.LocalTime.now());
	                        reportarAveria();
	                        engine=false;
	                    } 
						
						else if(estado && !conectado) {
				            System.out.println("Engine conectado en: " + hostEngine + ":" + puertoEngine + " - " + java.time.LocalTime.now());
				            reportarRecuperacion();
				            conectado=true;
				            ultimo=true;
				            
				            if(!engine) {
				            	conectarAEngine();
				            	engine=true;
				            }
				        } 
						else if (estado && !ultimo && conectado) {
	                        System.out.println("Engine Recuperado - " + java.time.LocalTime.now());
	                        reportarRecuperacion();
	                        
	                        if(!engine) {
				            	conectarAEngine();
				            	engine=true;
				            }
	                    }
						
						else if(estado && ultimo && conectado && !engine) {
							conectarAEngine();
			            	engine=true;
						}
						
						else if (estado && ultimo && conectado) {
	                        System.out.println("Engine OK - " + java.time.LocalTime.now());
	                    } 
						
						else if (!estado && !ultimo && conectado) {
							System.out.println("Engine KO - " + java.time.LocalTime.now());
	                    } 
						ultimo=estado;
						Thread.sleep(1000);
					} 
					catch (InterruptedException e) {
						System.out.println("Monitorización interrumpida");
						break;
	                }
					catch(Exception e) {
						System.err.println("Error en verificación automatica: " + e.getMessage());
						//se asume que si hay error es una averia
						if(ultimo) {
							reportarAveria();
							ultimo=false;
							engine=false;
						}
						try {
	                        Thread.sleep(1000);
	                    } 
						catch (InterruptedException ie) {
	                        break;
	                    }
					}
				}
			}
		});
		
		hilo.start();
	}
	    
}
