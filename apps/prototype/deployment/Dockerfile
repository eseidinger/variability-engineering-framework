FROM node:24.14.0-alpine AS ui-build

WORKDIR /workspace/ui

COPY apps/prototype/ui/package.json apps/prototype/ui/package-lock.json ./
RUN npm ci

COPY apps/prototype/ui/ ./
RUN npm run build


FROM maven:3.9.16-eclipse-temurin-25 AS api-build

WORKDIR /workspace/api

COPY apps/prototype/api/pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY apps/prototype/api/src/ src/
COPY --from=ui-build /workspace/ui/dist/variability-engineering-ui/browser/ src/main/resources/META-INF/resources/

RUN mvn --batch-mode --no-transfer-progress package -DskipTests


FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

ENV LANGUAGE="en_US:en" \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dvariability.fixture.directory=/deployments/fixture" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"

COPY --from=api-build --chown=10001:10001 /workspace/api/target/quarkus-app/lib/ /deployments/lib/
COPY --from=api-build --chown=10001:10001 /workspace/api/target/quarkus-app/*.jar /deployments/
COPY --from=api-build --chown=10001:10001 /workspace/api/target/quarkus-app/app/ /deployments/app/
COPY --from=api-build --chown=10001:10001 /workspace/api/target/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=10001:10001 examples/food-service/mvp-v1/ /deployments/fixture/

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
