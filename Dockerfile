# ==========================================
# ETAPA 1: BUILD (Compilación y Empaquetado)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar el POM y descargar dependencias para aprovechar el caché de capas de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar el JAR (saltando tests para optimizar tiempos de build en Render)
COPY src ./src
RUN mvn package -DskipTests -B

# ==========================================
# ETAPA 2: RUNTIME (Ejecución)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario no root + su-exec para arreglar permisos del volumen de uploads al arrancar
RUN addgroup -S spring && adduser -S spring -G spring \
    && apk add --no-cache su-exec \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app/uploads

# Copiar el archivo JAR compilado desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Exponer el puerto por defecto
EXPOSE 8080

ENTRYPOINT ["/docker-entrypoint.sh"]
