# Build context is backend/ (this module's parent pom is the reactor pom,
# so Maven needs the full reactor - both module poms - to resolve at all,
# even though only mock-provider-service is actually built here).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY payflow-core/pom.xml payflow-core/pom.xml
COPY mock-provider-service/pom.xml mock-provider-service/pom.xml
RUN mvn -q -pl mock-provider-service -am dependency:go-offline

COPY mock-provider-service mock-provider-service
RUN mvn -q -pl mock-provider-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S payflow && adduser -S payflow -G payflow
WORKDIR /app
COPY --from=build /build/mock-provider-service/target/mock-provider-service.jar app.jar
USER payflow
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
