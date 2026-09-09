#!/bin/bash
# Sobe as 3 agencias em background. Cada uma e o MESMO jar, com AGENCIA_ID diferente.
set -e
cd "$(dirname "$0")/agencia"

[ -f target/iceibank-agencia-1.0.0.jar ] || mvn -q -DskipTests package

for i in 0 1 2; do
  porta=$((4016 + i))
  AGENCIA_ID=$i SERVER_PORT=$porta setsid java -jar target/iceibank-agencia-1.0.0.jar \
      > "/tmp/iceibank-agencia-$i.log" 2>&1 < /dev/null &
  echo "agencia $i  ->  http://localhost:$porta   (log: /tmp/iceibank-agencia-$i.log)"
done

echo "aguardando subir..."
for _ in $(seq 1 15); do
  sleep 2
  if curl -s -o /dev/null --max-time 2 http://localhost:4018/status; then
    echo "as 3 agencias estao no ar."
    exit 0
  fi
done
echo "atencao: alguma agencia pode nao ter subido. Confira /tmp/iceibank-agencia-*.log"
