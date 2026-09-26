FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-spider-service ./crawler-spider-service

RUN mvn -pl crawler-spider-service -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/crawler-spider-service/target/*.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
