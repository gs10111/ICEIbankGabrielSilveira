#!/bin/bash
# Pre-voo: o broker esta acessivel DESTE computador?
#
# Rode isto no PC do laboratorio ANTES de apresentar. Sem Docker, o broker e
# remoto (CloudAMQP), e laboratorios costumam bloquear portas de saida que nao
# sejam 80/443. Se este teste falhar, a transferencia entre agencias nao vai
# funcionar na hora H -- e melhor descobrir agora, nao na frente da banca.
set -e
RAIZ="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${RABBITMQ_URL:-}" ] && [ -f "$RAIZ/.env.local" ]; then
  set -a; . "$RAIZ/.env.local"; set +a
fi
if [ -z "${RABBITMQ_URL:-}" ]; then
  echo "ERRO: defina RABBITMQ_URL (ou crie .env.local na raiz)." >&2
  exit 1
fi

HOST=$(echo "$RABBITMQ_URL" | sed -E 's|.*@([^:/]+).*|\1|')
case "$RABBITMQ_URL" in
  amqps://*) PORT=5671; TLS="sim (amqps)";;
  *)         PORT=5672; TLS="nao (amqp)";;
esac

echo "broker host : $HOST"
echo "porta       : $PORT   TLS: $TLS"
echo -n "testando TCP... "

if timeout 8 bash -c "cat < /dev/null > /dev/tcp/$HOST/$PORT" 2>/dev/null; then
  echo "OK -- o broker esta acessivel. Pode rodar ./subir-agencias.sh"
  exit 0
else
  echo "FALHOU"
  echo
  echo "A porta $PORT esta bloqueada ou o host nao responde. Opcoes:"
  echo "  1. Usar uma rede que libere a saida $PORT (ex.: roteador do celular)."
  echo "  2. Apresentar do proprio notebook (que tem Docker) usando so a tela do lab."
  echo "  3. Confirmar com o CloudAMQP se a instancia ainda esta ativa."
  exit 1
fi
