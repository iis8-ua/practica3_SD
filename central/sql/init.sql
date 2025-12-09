CREATE TABLE driver (
    id VARCHAR(20) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    saldo DECIMAL(10,2) DEFAULT 100.00,
    email VARCHAR(100),
    telefono VARCHAR(20),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE charging_point (
    id VARCHAR(20) PRIMARY KEY,
    ubicacion VARCHAR(200) NOT NULL,
    precio_kwh DECIMAL(10,4) NOT NULL,
    estado ENUM('ACTIVADO','PARADO','SUMINISTRANDO','AVERIADO','DESCONECTADO') DEFAULT 'DESCONECTADO',
    funciona BOOLEAN DEFAULT TRUE,
    registrado_central BOOLEAN DEFAULT FALSE,
    conductor_actual VARCHAR(20) NULL,
    consumo_actual DECIMAL(10,3) DEFAULT 0.0,
    importe_actual DECIMAL(10,3) DEFAULT 0.0,
    ultima_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conductor_actual) REFERENCES driver(id)
);

CREATE TABLE charging_session (
    session_id VARCHAR(50) PRIMARY KEY,
    cp_id VARCHAR(20) NOT NULL,
    conductor_id VARCHAR(20) NOT NULL,
    tipo ENUM('Manual','Automatico') NOT NULL,
    inicio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fin TIMESTAMP NULL,
    estado ENUM('EN_CURSO','FINALIZADA','CANCELADA') DEFAULT 'EN_CURSO',
    energia_total DECIMAL(10,3) DEFAULT 0.0,
    importe_total DECIMAL(10,3) DEFAULT 0.0,
    FOREIGN KEY (cp_id) REFERENCES charging_point(id),
    FOREIGN KEY (conductor_id) REFERENCES driver(id)
);

CREATE TABLE IF NOT EXISTS event_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    cp_id VARCHAR(50),
    tipo_evento VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cp_id) REFERENCES charging_point(id)
);