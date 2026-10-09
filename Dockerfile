# syntax=docker/dockerfile:1
#
# Build multi-stage JVM da management-api.
# O stage de build roda na plataforma do builder ($BUILDPLATFORM) — os jars
# gerados são independentes de arquitetura; a imagem final segue a plataforma
# alvo pedida ao buildx (linux/arm64 para a VM A1.Flex na OCI).
#
#   docker buildx build --platform linux/arm64 -t management-api .
#
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24
ENV LANGUAGE='en_US:en'

# Mesma estrutura em camadas do src/main/docker/Dockerfile.jvm.
COPY --from=build --chown=185 /workspace/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /workspace/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /workspace/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /workspace/target/quarkus-app/quarkus/ /deployments/quarkus/
# Chaves JWT carregadas por path relativo (ver application.properties); CWD = /deployments.
COPY --chown=185 keys/ /deployments/keys/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
