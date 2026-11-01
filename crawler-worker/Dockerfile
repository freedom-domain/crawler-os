FROM mcr.microsoft.com/playwright/java:v1.51.0-noble
WORKDIR /app
COPY target/crawler-worker-*.jar /app/app.jar
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
EXPOSE 8090
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
