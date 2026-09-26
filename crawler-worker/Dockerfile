FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-worker ./crawler-worker

RUN mvn -pl crawler-worker -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/crawler-worker/target/*.jar /app/app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
