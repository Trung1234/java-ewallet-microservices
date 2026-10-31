# Build the jar first: ./mvnw -pl services/notification-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/notification-service.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
