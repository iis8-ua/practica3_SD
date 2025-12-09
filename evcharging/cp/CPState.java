package p2.evcharging.cp;

public enum CPState {
	ACTIVADO("Activado", "VERDE"),
    PARADO("Parado", "NARANJA"),
    SUMINISTRANDO("Suministrando", "VERDE"),
    AVERIADO("Averiado", "ROJO"),
    DESCONECTADO("Desconectado", "GRIS");

    private final String descripcion;
    private final String color;

    CPState(String descripcion, String color) {
        this.descripcion = descripcion;
        this.color = color;
    }

	public String getDescripcion() {
		return descripcion;
	}

	public String getColor() {
		return color;
	}
    
}
