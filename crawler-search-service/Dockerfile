FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-search-service ./crawler-search-service

RUN mvn -pl crawler-search-service -am -DskipTests clean package -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/crawler-search-service/target/*.jar /app/app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
