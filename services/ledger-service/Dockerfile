# Build the jar first: ./mvnw -pl services/ledger-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ledger-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
