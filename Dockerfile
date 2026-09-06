# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copier le POM d'abord pour profiter du cache Docker
COPY pom.xml .

# Télécharger les dépendances Maven
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src ./src

# Construire le JAR Spring Boot
RUN mvn package -DskipTests -B


# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copier le JAR généré
COPY --from=builder /app/target/*.jar app.jar

# Port utilisé par l'application
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]