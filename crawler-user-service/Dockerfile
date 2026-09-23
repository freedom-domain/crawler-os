FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-user-service ./crawler-user-service

RUN mvn -pl crawler-user-service -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/crawler-user-service/target/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
