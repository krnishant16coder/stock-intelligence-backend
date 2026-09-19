# Build stage: uses Maven + Java 21 to produce the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Cache dependencies (speeds up rebuilds)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Run stage: minimal JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/stock-intelligence-0.1.0.jar app.jar
EXPOSE 8080
# Cloud Run injects $PORT; application.yml reads server.port=${PORT:8080}
ENTRYPOINT ["java", "-jar", "app.jar"]
