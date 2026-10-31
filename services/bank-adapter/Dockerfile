# Build the jar first: ./mvnw -pl services/bank-adapter -am package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/bank-adapter.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
