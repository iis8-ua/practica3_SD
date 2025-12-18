#!/bin/bash

# --- ENGINES (EV_CP_E) ---
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP001 "Snowy Road 123, Anchorage, Alaska" 0.15 localhost:7070 localhost:9092 8080; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP002 "Avenida Central 456, Alicante, ES" 0.18 localhost:7071 localhost:9092 8081; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP003 "Burj Khalifa Blvd, Dubai, AE" 0.16 localhost:7072 localhost:9092 8082; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP004 "Baker Street 221B, London, GB" 0.17 localhost:7073 localhost:9092 8083; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP005 "Shibuya Crossing, Tokyo, JP" 0.19 localhost:7074 localhost:9092 8084; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_E CP006 "Red Square, Moscow, RU" 0.14 localhost:7075 localhost:9092 8085; exec bash'

# --- MONITORES (EV_CP_M) ---
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8080 CP001 localhost:9092 8080; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8081 CP002 localhost:9092 8081; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8082 CP003 localhost:9092 8082; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8083 CP004 localhost:9092 8083; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8084 CP005 localhost:9092 8084; exec bash'
gnome-terminal -- bash -c 'java -cp "../p3/libs/*:.." p3.evcharging.cp.EV_CP_M localhost 8085 CP006 localhost:9092 8085; exec bash'

# --- DRIVERS ---
gnome-terminal --title="DRIVER001 - Pedro Perez" -- bash -c 'java -cp "../p3/libs/*:.." p3.driver.EV_Driver localhost:9092 DRIVER001; exec bash'
gnome-terminal --title="DRIVER002 - Jaime Torregrosa" -- bash -c 'java -cp "../p3/libs/*:.." p3.driver.EV_Driver localhost:9092 DRIVER002; exec bash'
gnome-terminal --title="DRIVER003 - Gonzalo Martinez" -- bash -c 'java -cp "../p3/libs/*:.." p3.driver.EV_Driver localhost:9092 DRIVER003; exec bash'
