# Multi-stage so `docker build` works from a plain checkout on the VPS, where there is no
# CI to run `mvn package` and produce target/*.jar first. The build stage compiles and
# repackages the Spring Boot jar; the runtime stage carries only the jar (same base as before).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Resolve dependencies on the pom layer so they're cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
