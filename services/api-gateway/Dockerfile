# Build the jar first: ./mvnw -pl services/api-gateway -am package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/api-gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
