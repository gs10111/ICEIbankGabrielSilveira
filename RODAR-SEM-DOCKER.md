# Rodar o ICEIBank SEM Docker (PC do laboratório)

O PC do laboratório não tem Docker Engine. Tudo bem: **o Docker nunca foi
necessário** — ele só empacotava o broker e subia tudo com um comando. Sem ele,
você roda as 3 agências como processos Java normais e o frontend como qualquer
site estático. A única peça que o Docker trazia e que você precisa substituir é o
**broker RabbitMQ**.

> Verificado em 2026-10-05: as 3 agências nativas, contra o CloudAMQP, completaram
> uma transferência entre agências pela fila (0→1, `970 + 830 = 1800`). Funciona.

---

## O que o PC do laboratório precisa ter

| Peça | Para quê | Como checar |
|---|---|---|
| **Java 21** (JRE basta p/ rodar) | rodar o `.jar` das agências | `java -version` → tem que dizer 21 |
| **Maven** (opcional) | *construir* o `.jar`, se ele não vier pronto | `mvn -v` |
| **Node 18+** (opcional) | rodar o frontend em modo dev | `node -v` |
| **Internet liberando a porta 5671** | falar com o broker CloudAMQP (remoto) | `./verificar-broker.sh` |

Maven e Node são **opcionais** porque dá para levar o `.jar` e o frontend já
compilados num pendrive (ver "Plano B" no fim). Java 21 e a porta 5671 **não** são
opcionais.

---

## O broker: por que é remoto, e o risco

Sem Docker não há broker rodando na sua máquina. A solução já configurada é o
**CloudAMQP** — um RabbitMQ hospedado, de graça. A URL (com usuário e senha) mora
em `.env.local` na raiz do projeto, que é **segredo** e não está no Git. Você
precisa levar esse arquivo (pendrive, não e-mail).

**O risco número 1 da apresentação:** laboratórios de faculdade costumam bloquear
portas de saída que não sejam 80/443. O CloudAMQP usa a **5671** (AMQP sobre TLS).
Se o firewall do lab bloquear a 5671, a transferência entre agências falha na hora.

**Por isso existe o `./verificar-broker.sh`: rode-o no lab ANTES de apresentar.**
Se ele passar, está tudo certo. Se falhar, veja o "Plano B".

---

## Passo a passo (o caminho normal)

Abra **dois terminais** na raiz do projeto.

### Terminal 1 — as 3 agências

```bash
./verificar-broker.sh        # pre-voo: a rede do lab deixa falar com o broker?
./subir-agencias.sh          # sobe as 3 agencias (4016, 4017, 4018)
```

`subir-agencias.sh` constrói o `.jar` sozinho se ele não existir (precisa de Maven),
lê o `RABBITMQ_URL` do `.env.local`, e só diz "as 3 agências estão no ar" depois de
confirmar as três portas respondendo.

### Terminal 2 — o frontend

**Se o lab tem Node:**
```bash
cd frontend
npm install      # só na primeira vez
npm run dev      # abre em http://localhost:5173
```

**Se o lab NÃO tem Node** (use o `dist/` já compilado):
```bash
cd frontend/dist
python3 -m http.server 5173     # abre em http://localhost:5173
```
O frontend descobre as agências por `window.location.hostname`, então servir o
`dist/` por qualquer servidor estático funciona — não precisa de configuração.

### Login e uso

Abra **http://localhost:5173**, conta **0**, senha **ana123**.

### Ao terminar

```bash
./derrubar-agencias.sh
```

---

## Demonstrações do Sprint 2 (os cenários que valem nota)

Com tudo no ar:

- **Transferência assíncrona:** transfira da conta 0 (ag. 0) para a conta 1 (ag. 1).
  O recibo diz *"Transferência publicada para a agência 1"* — ela viajou pela fila.
- **Resiliência (o print mais forte):** derrube **uma** agência e transfira para ela
  mesmo assim.
  ```bash
  fuser -k 4017/tcp            # derruba SO a agencia 1 (porta 4017)
  ```
  A transferência volta **HTTP 200**, a mensagem fica na fila do CloudAMQP (veja no
  painel do CloudAMQP, menu Queues), e quando a agência 1 voltar ela consome e o
  saldo fecha.
- **Linha do tempo causal:** menu *Linha do tempo* → os pares concorrentes aparecem
  marcados; ou no terminal:
  ```bash
  cd agencia && java -jar target/iceibank-agencia-1.0.0.jar --mesclar-logs data
  ```

> Atenção: sem Docker, cada agência grava os eventos em `agencia/data/`. Reiniciar
> uma agência **não** apaga o `.jsonl` (ao contrário do `docker compose down -v`),
> mas as **contas vivem em memória** e somem no restart — é isso que faz o cenário
> de resiliência doer, e é de propósito.

---

## Plano B — se a porta 5671 estiver bloqueada no lab

Em ordem de preferência:

1. **Roteador do celular (hotspot).** A saída 5671 quase sempre passa no 4G/5G.
   Conecte o PC do lab ao seu hotspot só para a apresentação.
2. **Apresentar do seu notebook** (que tem Docker) e usar apenas a tela/projetor do
   laboratório. É o caminho mais seguro se a rede do lab for imprevisível.
3. **Subir seu próprio broker** num outro CloudAMQP e trocar a `RABBITMQ_URL` do
   `.env.local` — útil se a instância atual (`gerbil.rmq.cloudamqp.com`) tiver
   expirado. O plano grátis do CloudAMQP leva 2 minutos para criar.

## Plano pendrive (lab sem Maven nem Node)

Numa máquina que tenha as ferramentas (a sua), gere os artefatos e leve prontos:

```bash
cd agencia && mvn -q -DskipTests package     # gera agencia/target/iceibank-agencia-1.0.0.jar
cd ../frontend && npm run build               # gera frontend/dist/
```

No pendrive vão: `agencia/target/iceibank-agencia-1.0.0.jar`, `frontend/dist/`,
os scripts `.sh` e o `.env.local`. No lab, com só **Java 21 + Python** (para servir
o `dist/`), você roda sem instalar nada.
