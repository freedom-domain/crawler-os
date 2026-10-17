FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/crawler-worker-*.jar /app/app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
