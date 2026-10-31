# Build the jar first: ./mvnw -pl services/wallet-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/wallet-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
