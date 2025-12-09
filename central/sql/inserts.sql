INSERT IGNORE INTO charging_point (id, ubicacion, estado, precio_kwh) VALUES
('CP001', 'Calle Fria 123, Oslo', 'DESCONECTADO', 0.15),
('CP002', 'Avenida Central 456, Alicante', 'DESCONECTADO', 0.18),
('CP003', 'Plaza Mayor 789, Madrid', 'DESCONECTADO', 0.16),
('CP004', 'Parque Industrial, Valencia', 'DESCONECTADO', 0.17),
('CP005', 'Centro Comercial Norte, Barcelona', 'DESCONECTADO', 0.19),
('CP006', 'Zona Universitaria, Sevilla', 'DESCONECTADO', 0.14);

INSERT IGNORE INTO driver (id, nombre) VALUES
('DRIVER001', 'Pedro Perez'),
('DRIVER002', 'Jaime Torregrosa'),
('DRIVER003', 'Gonzalo Martinez');