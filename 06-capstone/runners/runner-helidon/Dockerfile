# syntax=docker/dockerfile:1.6
#
# ConfSpeakerHub — Helidon MP runner image (multi-stage).
#   Stage 1: build the reactor up to runner-helidon; produces a thin runnable jar
#            plus a target/libs/ directory (Helidon does not deploy WARs and a
#            single shaded uber-jar double-registers its CDI producers, so the
#            supported thin-jar + libs layout is used).
#   Stage 2: slim JRE, copy the jar + libs, `java -jar`.
#
# Build context: repo root. Build with:
#   docker build -t confspeakerhub/helidon:dev -f 06-capstone/runners/runner-helidon/Dockerfile .

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

COPY pom.xml ./
COPY 00-domain/pom.xml                       00-domain/pom.xml
COPY 01-interceptors-observability/pom.xml   01-interceptors-observability/pom.xml
COPY 02-authorization/pom.xml                02-authorization/pom.xml
COPY 03-el-policy/pom.xml                    03-el-policy/pom.xml
COPY 04-rest-sse-async/pom.xml               04-rest-sse-async/pom.xml
COPY 05-jsonp-llm-streaming/pom.xml          05-jsonp-llm-streaming/pom.xml
COPY 06-capstone/pom.xml                     06-capstone/pom.xml
COPY 06-capstone/runners/runner-liberty/pom.xml   06-capstone/runners/runner-liberty/pom.xml
COPY 06-capstone/runners/runner-helidon/pom.xml   06-capstone/runners/runner-helidon/pom.xml
COPY 06-capstone/runners/runner-quarkus/pom.xml   06-capstone/runners/runner-quarkus/pom.xml
COPY bonus-reactive-messaging/pom.xml        bonus-reactive-messaging/pom.xml
RUN mvn -B -pl 06-capstone/runners/runner-helidon -am dependency:go-offline -DskipTests || true

COPY 00-domain                       00-domain
COPY 01-interceptors-observability   01-interceptors-observability
COPY 02-authorization                02-authorization
COPY 03-el-policy                    03-el-policy
COPY 04-rest-sse-async               04-rest-sse-async
COPY 05-jsonp-llm-streaming          05-jsonp-llm-streaming
COPY 06-capstone                     06-capstone
COPY feeds  feeds
COPY policy policy
RUN mvn -B -pl 06-capstone/runners/runner-helidon -am -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:25-jre
LABEL org.opencontainers.image.title="ConfSpeakerHub (Helidon MP)" \
      org.opencontainers.image.description="Jakarta EE Hidden Gems — capstone, Helidon MP runnable jar" \
      org.opencontainers.image.source="https://github.com/mehmandarov/jakarta-hidden-gems"
WORKDIR /app
COPY --from=build /src/06-capstone/runners/runner-helidon/target/runner-helidon.jar /app/app.jar
COPY --from=build /src/06-capstone/runners/runner-helidon/target/libs /app/libs
EXPOSE 9080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
