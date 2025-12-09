package p2.central;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.util.Arrays;

public class ServidorConcurrente {
	private KafkaProducer<String, String> productor;
    private KafkaConsumer<String, String> consumidor;
    private HiloServidor hilo;
    private boolean ejecucion;
    
    public ServidorConcurrente(KafkaProducer<String, String> productor, KafkaConsumer<String, String> consumidor) {
    	this.productor=productor;
    	this.consumidor=consumidor;
    	this.ejecucion=false;
    	
    	consumidor.subscribe(Arrays.asList(
    			"cp-registro",
    			"cp-estado",
    			"cp-autorizacion",
    			"actualizacion-recarga",
    			"fallo-cp",
    			"recuperacion-cp",
    			"monitor-registro",
    			"ticket",
    			"driver-solicitud",
    			"monitor-averias",
    			"monitor-estado"
    	));
    }
    
    public void iniciar() {
    	ejecucion=true;
    	this.hilo=new HiloServidor(productor, consumidor);
    	hilo.start();
    }
    
    public void detener() {
    	ejecucion=false;
    	try {
    		if(hilo!=null) {
    			hilo.detener();
    		}
    		
    		if(productor !=null) {
    			productor.close();
    		}
    		
    		if(consumidor !=null) {
    			consumidor.close();
    		}
    		System.out.println("Servidor concurrente detenido");
    	}
    	catch(Exception e) {
    		System.err.println("Error deteniendo servidor: " + e.getMessage());
    	}
    }

}
