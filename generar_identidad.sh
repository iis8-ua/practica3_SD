#!/bin/bash

if [ -z "$1" ]; then
  echo "Uso: ./generar_identidad.sh <ID_DEL_CP>"
  echo "Ejemplo: ./generar_identidad.sh CP002"
  exit 1
fi

ID=$1

# 1. Generar Clave Privada y Certificado (X.509)
# -nodes: No encriptar la clave privada con password (para que Java la lea directo)
# -subj: Configura el 'Subject' del certificado automáticamente sin preguntar
openssl req -x509 -newkey rsa:2048 -keyout ${ID}.key -out ${ID}.crt -days 365 -nodes -subj "/CN=${ID}/O=EVCharging/C=ES"

# 2. Convertir Clave Privada a formato PKCS#8 (Para que Java no se queje)
openssl pkcs8 -topk8 -inform PEM -outform PEM -in ${ID}.key -out ${ID}_java.key -nocrypt
