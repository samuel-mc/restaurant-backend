#!/bin/sh
set -e

# El volumen de uploads suele montarse como root; el proceso corre como spring.
mkdir -p /app/uploads
chown -R spring:spring /app/uploads

exec su-exec spring:spring java -jar /app/app.jar "$@"
