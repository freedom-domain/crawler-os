FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/crawler-ai-service-*.jar /app/app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
