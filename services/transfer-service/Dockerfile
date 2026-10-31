# Build the jar first: ./mvnw -pl services/transfer-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/transfer-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
