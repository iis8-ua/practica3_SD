package p2.evcharging.cp;

import p2.db.DBManager;
import java.sql.*;
import p2.evcharging.cp.network.CentralConnector;
import p2.evcharging.cp.service.ChargingSessionService;

public class ChargingPoint {
	private String id;
    private String ubicacion;
    private double precioKwh;
    private CPState estado; 
    /*
     El consumo actual (en tiempo real), el importe actual, y el id del conductor se ponen cuando 
     el estado del punto de recarga es de Suministrando, cuando no es ese no se asignan valores
     */
    private double consumoActual;
    private double importeActual;
    private String conductorActual;
    //Esta variable está a true o false dependiendo si funciona o no el CP
    private boolean funciona;
    private boolean registradoCentral;
    
    private CentralConnector conector;
    private ChargingSessionService servicio;
    
    public ChargingPoint(String id, String ubicacion, double precioKwh) {
    	this.id=id;
    	this.ubicacion=ubicacion;
    	this.precioKwh=precioKwh;
    	this.estado=CPState.DESCONECTADO;
    	this.funciona=true;
    	this.registradoCentral=false;
    	this.servicio=new ChargingSessionService(this);
    	this.consumoActual=0.0;
    	this.importeActual=0.0;
    }
   
    
   public boolean registroEnCentral(String dirKafka) {
	   try {
		   this.conector= new CentralConnector(dirKafka, this);
		   boolean exito =conector.registrarCentral();
		   
		   if(exito) {
			   this.registradoCentral=true;
			   this.estado=CPState.ACTIVADO;
			   System.out.println("CP " + id + " registrado correctamente en la Central");
			   actualizarCPenBD("ACTIVADO", true);
               registrarEvento("REGISTRO_CP", "Punto de carga registrado en central");
			   return true;
		   }
		   else {
			   this.estado=CPState.DESCONECTADO;
			   actualizarCPenBD("DESCONECTADO", true);
			   System.out.println("Imposible conectar con la CENTRAL");
			   return false;
		   }
	   } 
	   catch (Exception e) {
		   System.out.println("Imposible conectar con la CENTRAL");
		   System.err.println("ERROR conectando a central: " + e.getMessage());
		   this.estado=CPState.DESCONECTADO;
		   actualizarCPenBD("DESCONECTADO", true);
		   return false;
	   }
   }
   
   
   public void activar() {
	   if(registradoCentral && funciona) {
		   this.estado=CPState.ACTIVADO;
		   conector.enviarEstadoACentral();
		   actualizarCPenBD("ACTIVADO", true);
		   System.out.println("CP activado");
	   }
	   else {
		   System.out.println("No es posible la activación por averia o no esta registrado en la central");
	   }
   }
   
   public void parar() {
	   if(estado == CPState.SUMINISTRANDO) {
		   servicio.finalizarSuministro();
	   }
	   this.estado=CPState.PARADO;
	   conector.enviarEstadoACentral();
	   actualizarCPenBD("PARADO", true);
	   System.out.println("CP fuera de servicio");
   }
   
   
  public boolean iniciarSuministroManual(String conductorId) {
	  if(puedeIniciarSuministro()) {
		  boolean exito= servicio.iniciarSuministro(conductorId, "Manual");
		  if(exito) {
			  this.estado = CPState.SUMINISTRANDO;
	          this.conductorActual = conductorId;
	          actualizarCPenBD("SUMINISTRANDO", true);
	          return true;
		  }
		  return false;
	  }
	  else {
		  System.out.println("No se puede iniciar suministro - Estado: " + estado + ", Funciona: " + funciona);
          return false;
	  }
  }
  
  public boolean autorizarSuministro(String conductorId, String sesionId) {
	  if(puedeIniciarSuministro()) {
		  System.out.println("Autorizado el suministro para el conductor: " + conductorId);
		  conector.enviarAutorizacion(sesionId, conductorId, true);
		  registrarEvento("AUTORIZACION_OK", "Autorizado conductor " + conductorId);
          actualizarSesionInicio(sesionId, conductorId);
		  return true;
	  }
	  else {
		  System.out.println("Denegado el suministro --> No disponible");
		  conector.enviarAutorizacion(sesionId, conductorId, false);
		  registrarEvento("AUTORIZACION_DENEGADA", "Denegado conductor " + conductorId);
		  return false;
	  }
  }
  
  public boolean iniciarSuministroAutorizado(String conductorId, String sesionId) {
	  if(autorizarSuministro(conductorId, sesionId)) {
		  boolean exito= servicio.iniciarSuministro(conductorId, "Automatico");
		  if(exito) {
			  actualizarCPenBD("SUMINISTRANDO", true);
		  }
		  return exito;
	  }
	  return false;
  }
  
  private boolean puedeIniciarSuministro() {
      return estado == CPState.ACTIVADO && funciona && registradoCentral;
  }
  
  public void actualizarConsumo(double kw) {
	   if (estado == CPState.SUMINISTRANDO) {
		   this.consumoActual += kw;
		   this.importeActual = this.consumoActual * precioKwh;
		   conector.enviarActualizacionConsumo(consumoActual,importeActual);
	   }
  }
  
  public void finalizarSuministro() {
	  if(this.estado == CPState.SUMINISTRANDO) {
		  servicio.finalizarSuministro();
		  registrarEvento("CONFIRMACION",
                  "Suministro finalizado. Consumo total: " + consumoActual + " kWh, Importe: " + importeActual);
          actualizarSesionFin();
		  this.consumoActual=0.0;
		  this.importeActual=0.0;
		  this.conductorActual=null;
		  this.estado = CPState.ACTIVADO;
		  actualizarCPenBD("ACTIVADO", true);
	  }
  }
  
  
  public void setFunciona(boolean funciona) {
	  boolean anterior=this.funciona;
	  this.funciona=funciona;
	  
	  if(!funciona && anterior) {
		  if (this.estado != CPState.PARADO) {
			  this.estado = CPState.AVERIADO;
	      }
		  conector.reportarAveria();
		  registrarEvento("AVERIA", "Avería detectada en CP");
		  actualizarCPenBD(this.estado.name(), false);
		  System.out.println("Avería pasada a Central");
		  
		  if (estado == CPState.SUMINISTRANDO) {
			  finalizarSuministro();
		  }
	  }
	  else if (funciona && !anterior) {
		  if (this.estado == CPState.AVERIADO) {
			  this.estado = CPState.ACTIVADO;
	      }
		  conector.reportarRecuperacion();
		  registrarEvento("RECUPERACION", "Recuperación tras mantenimiento");
		  actualizarCPenBD(this.estado.name(), true);
		  System.out.println("Recuperación pasada a Central");
	  }
  }
  
  
  public void setFunciona2(boolean funciona) {
	  boolean anterior=this.funciona;
	  this.funciona=funciona;
	  
	  if(!funciona && anterior) {
		  if (this.estado != CPState.PARADO) {
			  this.estado = CPState.AVERIADO;
	      }
		  registrarEvento("AVERIA", "Avería detectada en CP");
		  actualizarCPenBD(this.estado.name(), false);
		  System.out.println("Avería pasada a Central");
		  
		  if (estado == CPState.SUMINISTRANDO) {
			  finalizarSuministro();
		  }
	  }
	  else if (funciona && !anterior) {
		  if (this.estado == CPState.AVERIADO) {
			  this.estado = CPState.ACTIVADO;
	      }
		  registrarEvento("RECUPERACION", "Recuperación tras mantenimiento");
		  actualizarCPenBD(this.estado.name(), true);
		  System.out.println("Recuperación pasada a Central");
	  }
  }
  
  public void procesarComandoCentral(String comando) {
	  String[] partes = comando.split("\\|");
	  String tipo = partes[0];
	  
	  switch(tipo) {
	  	case "Inicio":
	  		if(partes.length >= 3) {
	  			iniciarSuministroAutorizado(partes[1], partes[2]);
	  		}
	  		break;
	  		
	  	case "Parar":
	  		parar();
	  		break;
	  		
	  	case "Continuar":
	  		activar();
	  		break;
	  		
	  	case "Parada_Emergencia":
	  		if(estado == CPState.SUMINISTRANDO) {
	  			finalizarSuministro();
	  		}
	  		parar();
	  		break;
	  }	  
  }
  
  private void actualizarCPenBD(String nuevoEstado, boolean funciona) {
      try (Connection conn = DBManager.getConnection();
           PreparedStatement ps = conn.prepareStatement(
                   "UPDATE charging_point SET estado=?, funciona=?, conductor_actual=?, consumo_actual=?, importe_actual=?, ultima_actualizacion=NOW() WHERE id=?")) {
          ps.setString(1, nuevoEstado);
          ps.setBoolean(2, funciona);
          ps.setString(3, conductorActual);
          ps.setDouble(4, consumoActual);
          ps.setDouble(5, importeActual);
          ps.setString(6, id);
          ps.executeUpdate();
      } 
      catch (SQLException e) {
          System.err.println("[DB] Error actualizando estado CP: " + e.getMessage());
      }
  }
  
  public void actualizarEstadoBD() {
      try (Connection conn = p2.db.DBManager.getConnection();
           PreparedStatement ps = conn.prepareStatement(
               "UPDATE charging_point SET estado = ?, funciona = ?, registrado_central = ?, conductor_actual = ?, consumo_actual = ?, importe_actual = ?, ultima_actualizacion = CURRENT_TIMESTAMP WHERE id = ?")) {
              
          ps.setString(1, this.estado.name());
          ps.setBoolean(2, this.funciona);
          ps.setBoolean(3, this.registradoCentral);
          ps.setString(4, this.conductorActual);
          ps.setDouble(5, this.consumoActual);
          ps.setDouble(6, this.importeActual);
          ps.setString(7, this.id);
          ps.executeUpdate();
          System.out.println("[DB] Estado actualizado en BD para CP " + id);
      } 
      catch (SQLException e) {
          System.err.println("[DB] Error actualizando estado del CP: " + e.getMessage());
      }
  }

  private void actualizarSesionInicio(String sesionId, String conductorId) {
      try (Connection conn = DBManager.getConnection();
           PreparedStatement ps = conn.prepareStatement(
                   "INSERT INTO charging_session (session_id, cp_id, conductor_id, tipo, estado) VALUES (?, ?, ?, 'Automatico', 'EN_CURSO')")) {
          ps.setString(1, sesionId);
          ps.setString(2, id);
          ps.setString(3, conductorId);
          ps.executeUpdate();
      } 
      catch (SQLException e) {
          System.err.println("[DB] Error insertando sesión: " + e.getMessage());
      }
  }

  private void actualizarSesionFin() {
      try (Connection conn = DBManager.getConnection();
           PreparedStatement ps = conn.prepareStatement(
                   "UPDATE charging_session SET fin=NOW(), estado='FINALIZADA', energia_total=?, importe_total=? WHERE cp_id=? AND estado='EN_CURSO'")) {
          ps.setDouble(1, consumoActual);
          ps.setDouble(2, importeActual);
          ps.setString(3, id);
          ps.executeUpdate();
      } 
      catch (SQLException e) {
          System.err.println("[DB] Error cerrando sesión: " + e.getMessage());
      }
  }

  private void registrarEvento(String tipo, String descripcion) {
      try (Connection conn = DBManager.getConnection();
           PreparedStatement ps = conn.prepareStatement(
                   "INSERT INTO event_log (cp_id, tipo_evento, descripcion) VALUES (?, ?, ?)")) {
          ps.setString(1, id);
          ps.setString(2, tipo);
          ps.setString(3, descripcion);
          ps.executeUpdate();
      } 
      catch (SQLException e) {
          System.err.println("[DB] Error registrando evento: " + e.getMessage());
      }
  }
  
  public void setEstado(CPState estado) {
	  this.estado=estado;
  }
  
  public void setConductorActual(String conductor) {
		this.conductorActual=conductor;	
  }
  
  public void setRegistradoCentral(boolean registrado) {
	    this.registradoCentral = registrado;
  }
    
   public String getId() { 
	   return id; 
   }
   public String getUbicacion() { 
	   return ubicacion; 
   }
   public double getPrecioKwh() { 
	   return precioKwh; 
   }
   public CPState getEstado() { 
	   return estado; 
   }
   public double getConsumoActual() { 
	   return consumoActual; 
   }
   public double getImporteActual() { 
	   return importeActual; 
   }
   public String getConductorActual() { 
	   return conductorActual; 
   }
   public boolean getFunciona() { 
	   return funciona; 
   }
   public boolean getRegistradoCentral() { 
	   return registradoCentral; 
   }
   
   public ChargingSessionService getServicio() {
	   return servicio;
   }
   
   public CentralConnector getConector() {
	   return conector;
   }
   
   public void imprimirInfoCP() {
	   System.out.println("\n=== ESTADO COMPLETO CP " + id + " ===");
       System.out.println("Ubicación: " + ubicacion);
       System.out.println("Precio: " + precioKwh + " €/kWh");
       System.out.println("Estado: " + estado + " (" + estado.getColor() + ")");
       
       if(funciona) {
    	   System.out.println("Salud: OK");
       }
       else {
    	   System.out.println("Salud: AVERIADO");
       }
       
       if(registradoCentral) {
    	   System.out.println("Registrado: SÍ");
       }
       else {
    	   System.out.println("Registrado: NO");
       }
       
       if (estado == CPState.SUMINISTRANDO) {
           System.out.println("Conductor: " + conductorActual);
           System.out.println("Consumo actual: " + consumoActual + " kW");
           System.out.println("Importe actual: " + importeActual + " €");
       }
       System.out.println("=================================");
   }
    
}


