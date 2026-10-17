FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/crawler-search-service-*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
