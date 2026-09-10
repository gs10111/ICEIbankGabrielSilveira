# Roteiro para gerar as evidências

Todo print precisa mostrar a saída de `date` (ou `Get-Date` no PowerShell) em algum
terminal visível, comprovando execução recente — convenção do roteiro (§4.2).

## Preparação

```bash
./subir-agencias.sh                       # 3 agências: 4016, 4017, 4018
cd frontend && npm run dev                # http://localhost:5173
```

Guarde um token para os comandos `curl`:

```bash
TOKEN=$(curl -s -X POST localhost:4016/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"idConta":0,"senha":"ana123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
AUTH="Authorization: Bearer $TOKEN"
```

---

## 0. `agencias-no-ar.png` e `particao-recusa.png`

Nao estao na lista obrigatoria do roteiro, mas sao os dois prints que sustentam
todo o resto: provam que sao 3 processos do MESMO jar e que a particao e real.

```bash
date
ps -ef | grep "[i]ceibank-agencia" | awk '{print $2, $8, $9, $10}'   # 3 PIDs, mesmo jar
for p in 4016 4017 4018; do curl -s localhost:$p/status; echo; done
```
Aponte no print: as 3 agencias respondem `"contas":2` — e ninguem configurou isso
conta a conta. Cada agencia tentou criar as 6 contas de demonstracao e o
`Particionador` recusou as 4 que nao sao dela.

```bash
date
curl -s -o /dev/null -w "conta 1 na agencia 0: HTTP %{http_code}\n" -H "$AUTH" localhost:4016/contas/1
curl -s -o /dev/null -w "conta 1 na agencia 1: HTTP %{http_code}\n" -H "$AUTH" localhost:4017/contas/1
```
Esperado: **400** na agencia 0 e **200** na agencia 1. `1 % 3 = 1`.

---

## 1. `transferencia-local.png`

Transferência dentro da agência 0 (conta 0 → conta 3; ambas `% 3 == 0`).

```bash
date
curl -s localhost:4016/contas/0 -H "$AUTH"; echo
curl -s localhost:4016/contas/3 -H "$AUTH"; echo

curl -s -X POST localhost:4016/transferencias -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"idOrigem":0,"idDestino":3,"valor":100.00}'; echo

curl -s localhost:4016/contas/0 -H "$AUTH"; echo
curl -s localhost:4016/contas/3 -H "$AUTH"; echo
tail -2 agencia/data/eventos-agencia-0.jsonl
```

**Mostrar no print:** saldos antes e depois, e os dois eventos `TRANSFERENCIA_DEBITO`
e `TRANSFERENCIA_CREDITO` com carimbos de Lamport **consecutivos** — dois eventos
locais, sem envio nem recebimento.

---

## 2. `transferencia-entre-agencias.png`

Conta 0 (agência 0) → conta 1 (agência 1). **Inclua os logs das duas agências.**

```bash
date
curl -s -X POST localhost:4016/transferencias -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"idOrigem":0,"idDestino":1,"valor":50.00}'; echo

echo "--- log da agencia 0 (origem) ---"; tail -1 agencia/data/eventos-agencia-0.jsonl
echo "--- log da agencia 1 (destino) ---"; tail -1 agencia/data/eventos-agencia-1.jsonl
```

**O que apontar no print:** o `TRANSFERENCIA_CREDITO_REMOTO` na agência 1 tem carimbo
`max(relógio local, carimbo recebido) + 1`. Se a agência 1 estava em 1 e o envio veio
com 6, o crédito sai com **7**. É a regra 3 de Lamport atravessando a rede.

---

## 3. `falha-conhecida.png`

> **Este print mostra um erro que o roteiro MANDA reproduzir**, não um defeito.
> Seção 2: *"o que este sprint deliberadamente NÃO resolve ainda"*. Tarefa §8.2
> passo 5 e item 5 do checklist §13: *"a falha conhecida reproduzida e documentada,
> **não escondida**"*. Perder ponto aqui é não reproduzir; reproduzir é o requisito.

O `echo` abaixo existe para que a **própria imagem** carregue essa explicação —
quem olhar só o print não precisa ir atrás do README para entender.

```bash
date
echo "PARTE D - falha INTENCIONAL exigida pelo roteiro (secao 2 e tarefa 8.2 passo 5)."
echo "Sem transacao distribuida, o debito nao e revertido. Sprint 4 resolve com 2PC/Saga."

curl -s localhost:4016/contas/0 -H "$AUTH"; echo      # saldo ANTES

fuser -k 4017/tcp                                     # derruba SÓ a agência 1
sleep 2

curl -s -X POST localhost:4016/transferencias -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"idOrigem":0,"idDestino":1,"valor":20.00}' -w "\n[HTTP %{http_code}]\n"

curl -s localhost:4016/contas/0 -H "$AUTH"; echo      # saldo DEPOIS — NÃO revertido
tail -2 agencia/data/eventos-agencia-0.jsonl
```

**O que o print precisa mostrar:** o **502**, a mensagem dizendo que o débito foi
aplicado e não revertido, o saldo menor depois do erro, e os eventos
`TRANSFERENCIA_DEBITO` seguido de `TRANSFERENCIA_FALHOU`.

Repare que entre os dois eventos **falta um carimbo de Lamport**: ele foi consumido
pelo `aoEnviar()` da mensagem que nunca chegou. Vale apontar isso no vídeo — é a
regra 2 de Lamport visível num evento que não existe.

**Ao narrar, diga nesta ordem:** (1) isto é exigido pelo roteiro; (2) o sistema
**não esconde** — devolve 502 dizendo que o débito ficou aplicado e grava
`TRANSFERENCIA_FALHOU` com o saldo pós-débito, deixando rastro para reconciliação;
(3) o conserto correto é transação distribuída, assunto do Sprint 4.

Suba a agência 1 de novo depois:
```bash
cd agencia && AGENCIA_ID=1 SERVER_PORT=4017 setsid java -jar target/iceibank-agencia-1.0.0.jar > /tmp/ag1.log 2>&1 &
```

---

## 4. `linha-do-tempo.png`

```bash
date
cd agencia && java -jar target/iceibank-agencia-1.0.0.jar --mesclar-logs data
```

**O que o print precisa mostrar:** a lista ordenada por Lamport com pelo menos um
`<EMPATE>`, e a seção **Análise** explicando que eventos empatados em agências
diferentes são concorrentes.

Se não aparecer empate, gere eventos concorrentes — uma operação em cada agência
quase ao mesmo tempo (elas não trocam mensagem, então os contadores colidem):

```bash
for i in 0 1 2; do
  T=$(curl -s -X POST localhost:$((4016+i))/auth/login -H 'Content-Type: application/json' \
      -d "{\"idConta\":$i,\"senha\":\"$(echo ana123 bruno123 carla123 | cut -d' ' -f$((i+1)))\"}" \
      | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
  curl -s -X POST localhost:$((4016+i))/contas/$i/depositar \
    -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"valor":1}' > /dev/null &
done; wait
```

---

## 5. Autenticação (Parte F) — três prints

### `auth-sem-token.png`
```bash
date
curl -s localhost:4016/contas/0 -w "\n[HTTP %{http_code}]\n"
```
Esperado: **401** com `"token ausente: envie Authorization: Bearer <token>"`.

### `auth-com-token.png`
```bash
date
curl -s -X POST localhost:4016/auth/login -H 'Content-Type: application/json' \
  -d '{"idConta":0,"senha":"ana123"}'; echo
curl -s localhost:4016/contas/0 -H "$AUTH" -w "\n[HTTP %{http_code}]\n"
```
Esperado: o token e depois **200** com o saldo.

### `auth-token-expirado.png`
Pela interface: entre no frontend e clique em **"Expirar token"** na barra superior;
faça qualquer operação e capture a faixa de alerta *"Sessão expirada · HTTP 401"*.

Ou por linha de comando, com um token deliberadamente adulterado:
```bash
date
curl -s localhost:4016/contas/0 -H "Authorization: Bearer $TOKEN-adulterado" -w "\n[HTTP %{http_code}]\n"
```

---

## 6. Frontend (Parte G) — três prints mínimos

Com `npm run dev` rodando em `http://localhost:5173`:

- **`frontend-login.png`** — tela de login com o seletor de agência e as contas de demonstração
- **`frontend-transferencia.png`** — tela de transferência com o bloco **Rota calculada**
  visível (mude a conta de destino entre 3 e 1 para ver "local" virar "entre agências")
- **`frontend-erro.png`** — um erro na tela: tente sacar mais que o saldo e capture a
  faixa *"Requisição inválida · HTTP 400 · saldo insuficiente…"*

Vale capturar também `frontend-painel.png` (saldo + malha de agências) e
`frontend-linha-do-tempo.png` (empates destacados).

---

## 7. `funcionalidade-adicional.png`

Os três extras numa tela só:

```bash
date
echo "=== EXTRA 1: historico por conta ==="
curl -s "localhost:4016/contas/0/historico?limite=5" -H "$AUTH" | python3 -m json.tool | head -30

echo "=== EXTRA 2: idempotencia — mesma chave duas vezes ==="
curl -s -X POST localhost:4016/transferencias -H "$AUTH" -H 'Idempotency-Key: demo-001' \
  -H 'Content-Type: application/json' -d '{"idOrigem":0,"idDestino":3,"valor":10.00}'; echo
curl -s -X POST localhost:4016/transferencias -H "$AUTH" -H 'Idempotency-Key: demo-001' \
  -H 'Content-Type: application/json' -d '{"idOrigem":0,"idDestino":3,"valor":10.00}'; echo
echo "^ o segundo traz reenvio:true e o saldo debitou UMA vez so"
curl -s localhost:4016/contas/0 -H "$AUTH"; echo

echo "=== EXTRA 3: extrato consolidado (Ana tem conta 0 na ag0 e conta 4 na ag1) ==="
curl -s "localhost:4016/extrato-consolidado?contas=0,4" -H "$AUTH" | python3 -m json.tool
```

---

## 8. Testes verdes (opcional, mas forte no vídeo)

```bash
date
cd agencia && mvn test
```
Mostre `Tests run: 89, Failures: 0, Errors: 0`.
