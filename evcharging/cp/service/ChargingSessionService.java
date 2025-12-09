package p2.evcharging.cp.service;

import p2.evcharging.cp.ChargingPoint;
import p2.evcharging.cp.CPState;


public class ChargingSessionService {
	private ChargingPoint cp;
	private boolean suministro;
	private Thread hilo;
	
	public ChargingSessionService(ChargingPoint cp) {
		this.cp=cp;
	}
	
	public boolean iniciarSuministro(String conductorId, String tipo) {
		   if(!suministro && cp.getEstado()==CPState.ACTIVADO) {
			   cp.setEstado(CPState.SUMINISTRANDO);
			   cp.setConductorActual(conductorId);
			   suministro=true;
			   
			   System.out.println("Suministrando " + tipo + "para: " + conductorId);
			   iniciarSimulacionConsumo();
			   return true;
			   
		   }
		   return false;
	}
	
	private void iniciarSimulacionConsumo() {
		hilo = new Thread(() -> {
			try {
				
				while(suministro && cp.getEstado()==CPState.SUMINISTRANDO) {
					double consumoSegundo = 0.1;
					cp.actualizarConsumo(consumoSegundo);
					
					//envia a la central la actulizacion del consumo en cada segundo como se indica en el enunciado
					if(cp.getConector() !=null) {
						cp.getConector().enviarActualizacionConsumo(cp.getConsumoActual(), cp.getImporteActual());
					}
					
					Thread.sleep(1000);
				}
			}
			catch(Exception e) {
				Thread.currentThread().interrupt();
			}
			
		});
		hilo.start();
	}
	
	public void finalizarSuministro() {
		suministro=false;
		
		if(hilo!=null && hilo.isAlive()) {
			hilo.interrupt();
		}
		
		System.out.println("Suministro finalizado para: " + cp.getConductorActual());
		System.out.println("\n=== FACTURA FINAL ===");
	    System.out.println("CP: " + cp.getId());
	    System.out.println("Conductor: " + cp.getConductorActual());
	    System.out.println("Ubicación: " + cp.getUbicacion());
	    System.out.println("Precio: " + cp.getPrecioKwh() + " €/kWh");
	    System.out.println("────────────────────");
	    System.out.printf("Consumo total: %.2f kW%n", cp.getConsumoActual());
	    System.out.printf("Importe total: %.2f €%n", cp.getImporteActual());
	    System.out.println("=====================\n");
	    
	    if(cp.getConector() != null) {
	    	cp.getConector().enviarTicket(cp.getConductorActual(), cp.getConsumoActual(), cp.getImporteActual());
	    }
	    
        cp.setEstado(CPState.ACTIVADO);
        cp.setConductorActual(null);
	}
	
	public boolean getSuministro() {
		return suministro;
	}

}
