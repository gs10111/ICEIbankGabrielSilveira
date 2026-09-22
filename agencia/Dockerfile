# ---------------------------------------------------------------- build
# Multi-stage: o Maven e o JDK completo ficam SO na etapa de build. A imagem final
# leva apenas o JRE e o jar — sai de ~800 MB para ~250 MB, e nao carrega compilador
# nem repositorio .m2 para producao.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O pom vem sozinho primeiro de proposito: enquanto ele nao mudar, o Docker reaproveita
# a camada de dependencias e nao baixa a internet inteira a cada alteracao de codigo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests
# -DskipTests aqui de proposito: os testes rodam em `mvn test` na maquina e no CI.
# Rodar de novo na imagem so aumentaria o tempo de build sem cobrir nada novo.

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl so para o HEALTHCHECK do compose conseguir perguntar /status.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Nao rodar como root: se alguem escapar do processo, escapa para um usuario sem nada.
RUN useradd --system --create-home --uid 10001 iceibank
COPY --from=build /build/target/iceibank-agencia-1.0.0.jar app.jar
RUN mkdir -p /app/data && chown -R iceibank:iceibank /app
USER iceibank

# O log de eventos (.jsonl) precisa sobreviver ao restart do container: o relogio
# vetorial e restaurado a partir dele no boot.
VOLUME ["/app/data"]

# Defaults; o compose sobrescreve. O MESMO jar vira as 3 agencias (12-Factor III).
ENV AGENCIA_ID=0 \
    AGENCIA_TOTAL=3 \
    SERVER_PORT=4016 \
    AGENCIA_PASTA_DADOS=/app/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 4016
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/status" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
