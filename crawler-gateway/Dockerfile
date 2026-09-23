FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-gateway ./crawler-gateway

RUN mvn -pl crawler-gateway -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/crawler-gateway/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
