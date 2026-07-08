# Build context is backend/ (this module's parent pom is the reactor pom,
# so Maven needs the full reactor - both module poms - to resolve at all,
# even though only payflow-core is actually built here).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY payflow-core/pom.xml payflow-core/pom.xml
COPY mock-provider-service/pom.xml mock-provider-service/pom.xml
RUN mvn -q -pl payflow-core -am dependency:go-offline

COPY payflow-core payflow-core
RUN mvn -q -pl payflow-core -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S payflow && adduser -S payflow -G payflow
WORKDIR /app
COPY --from=build /build/payflow-core/target/payflow-core.jar app.jar
USER payflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
