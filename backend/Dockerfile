FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/learning-platform-1.1.0-rc.1.jar app.jar
RUN mkdir -p /app/uploads /app/reports
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
