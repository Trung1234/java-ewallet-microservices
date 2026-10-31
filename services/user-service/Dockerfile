# Build the jar first: ./mvnw -pl services/user-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/user-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
