FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 10001 iceibank
COPY --from=build /build/target/iceibank-agencia-1.0.0.jar app.jar
RUN mkdir -p /app/data && chown -R iceibank:iceibank /app
USER iceibank

VOLUME ["/app/data"]

ENV AGENCIA_ID=0 \
    AGENCIA_TOTAL=3 \
    SERVER_PORT=4016 \
    AGENCIA_PASTA_DADOS=/app/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 4016
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/status" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
