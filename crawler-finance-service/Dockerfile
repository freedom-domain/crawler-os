FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn -pl crawler-finance-service -am clean package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/crawler-finance-service/target/crawler-finance-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
