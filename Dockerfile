# ==============================================================================
# Dockerfile Multi-Stage Optimizado para Render.com (Spring Boot + OpenJDK 21)
# ==============================================================================

# ETAPA 1: BUILD (Compilación y empaquetado del JAR)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copiar configuración Maven para aprovechar el caché de capas de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y empaquetar JAR omitiendo ejecuciones de test en build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ETAPA 2: RUNTIME (Imagen ligera de producción JRE 21 Alpine)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Instalar su-exec y herramientas mínimas de salud
RUN apk add --no-cache su-exec curl

# Crear usuario sin privilegios para ejecución segura en contenedor
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app/uploads

# Copiar el artefacto JAR empaquetado y el script de punto de entrada
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Exponer el puerto de Render (por defecto 8080 o dinamizado vía $PORT)
EXPOSE 8080

# Opciones de memoria JVM optimizadas para plan gratuito / starter de Render
ENV JAVA_OPTS="-Xms256m -Xmx448m -XX:+UseG1GC -XX:+UseStringDeduplication"

# Comprobación de Salud (Health Check) mediante Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
