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
# Confere as TRES portas: antes so a 4018 era testada, entao o script anunciava
# sucesso mesmo com uma agencia morta — e a demonstracao quebrava so na hora H.
for _ in $(seq 1 20); do
  sleep 2
  no_ar=0
  for porta in 4016 4017 4018; do
    curl -s -o /dev/null --max-time 2 "http://localhost:$porta/status" && no_ar=$((no_ar + 1))
  done
  if [ "$no_ar" -eq 3 ]; then
    echo "as 3 agencias estao no ar."
    exit 0
  fi
done

echo "ERRO: apenas $no_ar de 3 agencias subiram. Confira /tmp/iceibank-agencia-*.log" >&2
exit 1
