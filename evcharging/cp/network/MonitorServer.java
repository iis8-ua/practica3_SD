package p2.evcharging.cp.network;

import p2.evcharging.cp.ChargingPoint;
import p2.evcharging.cp.EV_CP_E;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MonitorServer {
	private ChargingPoint cp;
    private int puerto;
    private ServerSocket serverSocket;
    private boolean ejecucion;
	
	public MonitorServer(ChargingPoint cp, int puerto) {
		this.cp=cp;
		this.puerto=puerto;
		this.ejecucion=false;
	}

	public void iniciar() {
		try {
			serverSocket= new ServerSocket(puerto);
			ejecucion=true;
			
			while(ejecucion) {
				try {
					Socket socket= serverSocket.accept();
					procesarConexion(socket);
					socket.close();
				}
				catch(IOException e) {
					if(ejecucion) {
						System.err.println("Error aceptando la conexion: " + e.getMessage());
					}
				}
			}
		}
		catch(IOException e) {
			System.err.println("Error iniciando la monitorización del engine: " + e.getMessage());
		}
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
	
	 private void procesarConexion(Socket socket) {
		 try { 
	         String mensaje = leerDatos(socket);
	         //System.out.println("Se ha recibido: " + mensaje);
	         
	         if("Comprobar_Funciona".equals(mensaje)) {
	        	 if(cp.getFunciona()) {
	        		 escribirDatos(socket, "Funciona_OK");
	        	 }
	        	 else {
	        		 escribirDatos(socket, "Funciona_KO");
	        	 }
	         }
		 }
		 catch(Exception e) {
			 System.err.println("Error procesando la conexion: " + e.getMessage());
		 }
	 }
	
	
	public void detener() {
		ejecucion=false;
		try {
			if(serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
			System.out.println("Monitorización detenida");
		}
		catch(IOException e) {
			System.err.println("Error deteniendo MonitorServer: " + e.getMessage());
		}
		
	}

}
