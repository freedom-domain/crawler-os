FROM eclipse-temurin:21-jre
WORKDIR /app
ARG SERVICE
COPY ${SERVICE}/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
