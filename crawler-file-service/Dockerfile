FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-file-service ./crawler-file-service

RUN mvn -pl crawler-file-service -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/crawler-file-service/target/*.jar /app/app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
