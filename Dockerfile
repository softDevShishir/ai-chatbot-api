# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source for faster rebuilds
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S chatbot && adduser -S chatbot -G chatbot

COPY --from=build /app/target/ai-chatbot-api.jar app.jar
RUN chown chatbot:chatbot app.jar
USER chatbot

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
