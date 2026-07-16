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

# Crear un usuario no root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el archivo JAR compilado desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Exponer el puerto por defecto
EXPOSE 8080

# Comando para ejecutar la aplicación con hilos virtuales activos por JVM si es necesario
ENTRYPOINT ["java", "-jar", "app.jar"]
