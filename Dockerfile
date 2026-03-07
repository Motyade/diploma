# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build

# Copy source code
COPY pom.xml .
COPY api-spec api-spec
COPY app app

# Build project (compiles api-spec and app)
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/app/target/app-*.jar app.jar

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
