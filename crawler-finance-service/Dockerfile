FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY crawler-common ./crawler-common
COPY crawler-finance-service ./crawler-finance-service

RUN mvn -f crawler-common/pom.xml -DskipTests install -q \
    && mvn -f crawler-finance-service/pom.xml -DskipTests clean package -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/crawler-finance-service/target/*.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
