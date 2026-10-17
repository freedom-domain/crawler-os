FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY crawler-common/pom.xml crawler-common/pom.xml
COPY crawler-gateway/pom.xml crawler-gateway/pom.xml
COPY crawler-user-service/pom.xml crawler-user-service/pom.xml
COPY crawler-finance-service/pom.xml crawler-finance-service/pom.xml
COPY crawler-spider-service/pom.xml crawler-spider-service/pom.xml
COPY crawler-search-service/pom.xml crawler-search-service/pom.xml
COPY crawler-file-service/pom.xml crawler-file-service/pom.xml
COPY crawler-worker/pom.xml crawler-worker/pom.xml

RUN mvn -B -ntp -pl crawler-finance-service -am dependency:go-offline -DskipTests

COPY crawler-common/src/ crawler-common/src/
COPY crawler-finance-service/src/ crawler-finance-service/src/
RUN mvn -B -ntp -pl crawler-finance-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/crawler-finance-service/target/crawler-finance-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Dnacos.logging.default.config.enabled=false", "-jar", "/app/app.jar"]
