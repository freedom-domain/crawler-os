FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/crawler-gateway-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
