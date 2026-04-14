FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY request-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
