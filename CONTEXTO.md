# Contexto do projeto — onde as coisas estão e por quê

Documento de passagem de bastão. Serve para quem chega ao repositório sem ter
acompanhado o caminho: o aluno retomando depois de uma pausa, um professor querendo
entender uma decisão, ou um assistente de IA numa sessão nova.

**Última atualização:** 2026-09-24 · HEAD `61005f4` · sincronizado com `origin/main`.

---

## 1. O que é

ICEIBank — projeto da disciplina *Laboratório de Desenvolvimento de Aplicações Móveis
e Distribuídas*, PUC Minas / ICEI. Quatro sprints, 20 pontos cada, individual.

Aluno: **gabriel silveira**, RA **1466316**. OFFSET pessoal **16** → portas 4016/4017/4018.

| Sprint | Unidade | Tecnologia | Conceito distribuído |
|---|---|---|---|
| 1 ✅ | U2 — Web | REST / MVC | Relógio de Lamport |
| **2 🔨** | U3 — Comunicação indireta | RabbitMQ / Pub-Sub | **Relógio vetorial** |
| 3 | U4 — Móvel | Flutter | Consenso (eleição de líder) |
| 4 | U5 — Nuvem | Containers | Transações distribuídas (2PC/Saga) |

Um banco particionado em **3 agências**. A conta *N* pertence à agência `N % 3`. As três
agências são o **mesmo jar** com `AGENCIA_ID` diferente (12-Factor III). Todo evento que
altera estado é carimbado com o relógio lógico e gravado em `.jsonl`.

O roteiro do Sprint 2 está em `~/puicei/aaaaa/` (fora do repositório).

---

## 2. Estado agora

- **`origin/main` = `61005f4`**, árvore limpa, sem divergência.
- **110 testes** no backend (`cd agencia && mvn test`) + **5** no frontend (`cd frontend && npm test`). Todos verdes.
- A pilha está **no ar em container** (`docker compose ps` → 5/5 healthy).
- Autor único em todos os 37 commits: `gabriel silveira`. **Zero trailers de coautoria de IA** — isto é regra do projeto, ver §7.

### Sprint 2 — 12 de 20 pontos entregues

| Item | Pts | Estado |
|---|---|---|
| Parte A — RabbitMQ configurado | 3 | ✅ exchange topic durável, 3 filas, routing keys, verificado no broker real |
| Parte B — Relógio vetorial | 5 | ✅ as 3 regras + comparação de vetores, 21 testes |
| Parte C — Publish/Subscribe | 4 | ✅ inclui o teste de resiliência do roteiro (7.4, passos 3-5) |
| Continuidade (JWT, frontend, partição) | 2 | ✅ verificado ao vivo e em container, não presumido |
| Commits incrementais | 1 | ✅ 4 commits no sprint, nenhum "commitão" |
| **Parte D — Linha do tempo causal** | **3** | ⬜ **próximo passo** |
| **Funcionalidade adicional** | **1** | ⬜ |
| **Respostas às 10 questões** | **1** | ⬜ `RESPOSTAS.md` ainda só tem o Sprint 1 |

Faltam também os **3 prints** de `evidencias/sprint2/` (a pasta está vazia) e a
**declaração de uso de IA do Sprint 2**. Ver §6.

---

## 3. Como rodar

### Com Docker — um comando, nada instalado

```bash
docker compose up --build
```

| Endereço | O quê |
|---|---|
| http://localhost:5173 | frontend — conta **0**, senha `ana123` |
| http://localhost:4016 · 4017 · 4018 | as 3 agências |
| http://localhost:15672 | painel do RabbitMQ (`guest` / `guest`) |

Sobe o próprio RabbitMQ — **não precisa de conta no CloudAMQP**. `docker compose down -v`
apaga os dados (`.jsonl` e filas).

### Sem Docker

Precisa de um broker. A URL é **segredo** e mora em `.env.local` na raiz (gitignored):

```
RABBITMQ_URL=amqps://usuario:senha@host.cloudamqp.com/vhost
```

O `subir-agencias.sh` lê esse arquivo sozinho.

```bash
cd agencia && mvn clean package
./subir-agencias.sh          # sobe as 3; sai != 0 se alguma faltar
cd frontend && npm run dev   # localhost:5173
./derrubar-agencias.sh
```

> A instância CloudAMQP usada no desenvolvimento é a `littlelemur` (plano grátis). A
> credencial **nunca** foi commitada. Se o repositório virar público, rotacione a
> instância por precaução.

---

## 4. Decisões que o aluno precisa defender

O roteiro exige que ele explique e defenda qualquer trecho entregue. Estas são as
decisões não óbvias, com o porquê.

### Do Sprint 2

**O relógio vetorial substituiu o de Lamport — e a interface selada saiu junto.**
No Sprint 1, `Carimbo` era uma `sealed interface` com um `permits` só. Ela existia para
uma coisa específica: quando o vetorial entrasse, acrescentar um permit quebraria a
compilação **exatamente nos pontos que precisavam mudar**. Foram 4: `RelogioLamport`,
`RegistroDeEventos`, `EventoResposta`, `AgenciaRemota`. Cumprido o papel, ela voltou a ter
uma implementação só — e foi apagada, pela mesma regra que apagou oito portas no commit
`abe3186`. A abstração guiou uma mudança e depois saiu.

**`CarimboVetorial` guarda `List<Integer>`, não `int[]`.** Record com array não tem
igualdade por valor (array compara por identidade), e o carimbo precisa de
`equals`/`hashCode` para viver em `Set` — inclusive no teste de 100 threads, que conta
vetores distintos.

**`comparar()` devolve QUATRO respostas** (`IGUAIS`, `ANTES`, `DEPOIS`, `CONCORRENTES`), e
é por isso que `Comparable` seria o contrato errado. `Comparable` promete ordem **total**;
o vetorial dá ordem **parcial**. Essa decisão foi registrada em `RESPOSTAS.md` 10.3.2 no
Sprint 1, **antes** de existir vetorial nenhum — o argumento estava no papel e virou código.

**A pré-checagem síncrona do destino saiu.** No Sprint 1 a origem perguntava à agência de
destino se a conta existia, antes de debitar. Com mensageria isso não pode existir:
perguntar traria de volta exatamente o acoplamento síncrono que o sprint remove. Quem
descobre que a conta não existe é o **consumidor**, e ele registra `CREDITO_REMOTO_FALHOU`.

**`POST /contas/{id}/creditar-remoto` foi apagado.** Creditar deixou de ser rota — virou
mensagem, que não passa por filtro nem por HTTP. O `X-Agencia-Token` continua guardando
`/contas/{id}/interno`, que é a leitura do extrato consolidado. Isso materializa a
resposta da questão 7.5.3 do roteiro.

**A falha do Sprint 1 não sumiu: encolheu e mudou de lugar.** Destino fora do ar deixou de
ser problema (a mensagem espera na fila). Mas se o **broker** cair, o débito já aconteceu e
o crédito não chegou nem a ser publicado → `BrokerIndisponivelException` → HTTP **503**,
com `TRANSFERENCIA_DEBITO` + `TRANSFERENCIA_FALHOU` no log. É o que o Sprint 4 fecha.

**O recibo mudou de texto, e não é cosmético.** Era *"Transferência concluída"*; agora é
*"Transferência publicada para a agência N"*. HTTP 200 agora significa "a mensagem não vai
mais se perder", **não** "o dinheiro chegou".

**`default-requeue-rejected: false`.** Mensagem que falha não volta para a fila: a conta que
não existe agora não vai passar a existir na próxima tentativa, e reentregar em loop
travaria o consumidor.

### Do Sprint 1, ainda válidas

- **`synchronized` no relógio e na `Conta`**: `contador++` e `saldo.add()` são
  ler-modificar-escrever. O teste de 100 threads pegou a corrida de verdade
  (`expected: <100> but was: <99>`).
- **JWT em HS256 explícito.** Sem informar o algoritmo, a jjwt escolhia pelo tamanho da
  chave e o token saía em HS384.
- **Duas autenticações:** JWT identifica *pessoa*, `X-Agencia-Token` identifica *processo*.
  Evita *confused deputy*.
- **Autorização por recurso NÃO existe** — documentado em `RESPOSTAS.md` 11.3.1 por
  escolha própria, com evidência. Qualquer JWT válido opera qualquer conta da agência.
- **Sem persistência**: contas vivem em memória. Reiniciar uma agência apaga tudo o que
  foi criado — e é exatamente isso que faz o cenário de resiliência do Sprint 2 doer.

---

## 5. Armadilhas conhecidas

**`pkill -f "iceibank-agencia..."` mata quem o chama.** O `derrubar-agencias.sh` usa
`[i]ceibank` e se protege — mas **não protege quem o executa**. Se a sua linha de comando
contiver a string literal do nome do jar, o pkill casa com ela e mata o seu shell
(sintoma: exit **144**). Aconteceu três vezes. Regra: nunca mencione o nome do jar na mesma
linha em que roda o script.

**`%2F` no fim da URL do RabbitMQ.** `amqp://user:pass@host:5672/` pede o vhost de nome
**vazio**; o vhost padrão do RabbitMQ se chama `/`. O erro é
`NOT_ALLOWED - vhost  not found` (repare no espaço duplo — é o nome vazio aparecendo).

**`localhost` dentro de container resolve para IPv6.** O healthcheck do nginx falhava com
*connection refused* com o site respondendo 200 de fora. Use `127.0.0.1`.

**Renomear tipo em massa quebra classe aninhada de teste.** Uma troca mecânica de
`Carimbo` → `CarimboVetorial` renomeou também `@Nested class Carimbo` dos testes, que
passou a **sombrear** o tipo real. Aconteceu duas vezes (`Carimbo` e `CreditoRemoto`).
Sintoma: `cannot find symbol: method idConta()` num record que claramente tem o método.

**`mvn test-compile` mente com build incremental.** Depois de mudar assinatura em `main/`,
o `test-compile` pode dar BUILD SUCCESS sobre classes velhas. Use `mvn clean test-compile`.

**O remote é compartilhado com outras sessões.** Rode `git fetch` antes de afirmar
qualquer coisa sobre o estado da entrega.

**`origin` está em SSH** (`git@github.com:gs10111/...`). Em HTTPS todo push falha com
`could not read Username`.

---

## 6. O que só o gabriel pode fazer

1. **Os 3 prints de `evidencias/sprint2/`** — a pasta está vazia. O roteiro pede:
   - `transferencia-assincrona.png` — transferência entre agências completando via fila,
     com o log das duas agências visível
   - `resiliencia-fila.png` — agência de destino derrubada, transferência publicada mesmo
     assim, e o que acontece quando ela volta
   - `linha-do-tempo-causal.png` — a saída do merge de logs com ao menos um par concorrente
   - (mais um print da funcionalidade adicional)

   Os dois primeiros cenários **já foram reproduzidos e funcionam** — falta capturar.

2. **A declaração de uso de IA do Sprint 2.** A do Sprint 1 está assinada no fim do
   `RESPOSTAS.md`; a do Sprint 2 precisa ser escrita e assinada por ele.

3. **Decisões de escopo**: qual funcionalidade adicional implementar (§8).

---

## 7. Regras de trabalho deste repositório

**Autoria exclusiva `gs10111`.** Nenhum commit ou PR leva `Co-Authored-By` de IA nem
qualquer marca de geração automática. Isto **sobrepõe** o padrão da ferramenta. Não é
ocultação: a divulgação do uso de IA existe, no lugar certo — a seção "Declaração de uso de
IA" do `RESPOSTAS.md`, que é ele quem assina. Em 2026-09-22 os 7 commits que tinham o
trailer foram reescritos e republicados por decisão dele.

**TDD, sempre.** Teste vermelho antes do verde. Foi assim em todos os consertos: a corrida
de saldo, a restauração do relógio, o relógio vetorial, o consumidor.

**Sem abstração que não entregue variação real.** Antes de propor uma interface, conte
quantas implementações existem hoje. Se for uma, é classe concreta. Foi ele quem pediu para
trocar Ports & Adapters por MVC: *"ficou muito complexo coisas simples"*.

**Commits pequenos e incrementais**, com mensagem que explica o **porquê**, não o quê.

**Verificar, não presumir.** Toda afirmação sobre comportamento neste projeto foi medida:
`curl` contra a agência no ar, `rabbitmqctl` contra o broker, print lido antes de citado.

---

## 8. Próximo passo

**Parte D — linha do tempo causal (3 pontos).** O que falta:

- `MesclarLogs.java` já lê os vetores e lista os eventos, mas **ainda não identifica pares
  concorrentes**. A comparação já existe pronta e testada em
  `CarimboVetorial.comparar(a, b)` → `Relacao.CONCORRENTES`. Falta o laço que compara pares
  de eventos de **agências diferentes** e imprime os concorrentes.
- A mesma análise na tela `LinhaDoTempo.jsx` do frontend (hoje ela lista os vetores mas
  não marca concorrência).
- O roteiro pede provar os dois lados: um par **concorrente** (duas agências trabalhando
  sozinhas) e um par **causal** (uma transferência entre agências, que **não** pode
  aparecer como concorrente). Os dois casos já estão travados em teste unitário no
  `RelogioVetorialTest`.

**Depois:** funcionalidade adicional (1 ponto). Candidatos, em ordem de valor:

1. **Deduplicação no consumidor** — a entrega do RabbitMQ é *at-least-once*, então uma
   reentrega hoje creditaria duas vezes. O par `(origemAgencia, vetorEnvio)` já identifica
   cada mensagem de forma única — `CreditoRemoto.identidade()` já existe. É o único
   candidato que corrige uma **incorreção real**.
2. Dead-letter queue para as mensagens que falham (hoje são descartadas).
3. Fila de auditoria escutando `agencia.*.creditar` — a exchange já é *topic* justamente
   para permitir o curinga.

**E por fim:** as 10 questões do roteiro (seções 6.4, 7.5 e 8.3) em `RESPOSTAS.md`. Várias
já têm resposta pronta em forma de teste ou de evidência medida — estão apontadas nos
comentários do código.

---

## 9. Mapa dos arquivos que importam

```
agencia/src/main/java/br/pucminas/iceibank/
  modelo/relogio/RelogioVetorial.java     as 3 regras do relógio vetorial
  modelo/relogio/CarimboVetorial.java     o carimbo + comparar() → Relacao
  modelo/relogio/Relacao.java             IGUAIS / ANTES / DEPOIS / CONCORRENTES
  servico/TransferenciaService.java       local vs entre agências; onde cada regra entra
  servico/Publicador.java                 publica o crédito na exchange
  mensageria/ConsumidorDeCreditos.java    consome; trata conta inexistente sem travar a fila
  mensageria/CreditoRemoto.java           o corpo da mensagem
  config/MensageriaConfig.java            exchange, filas, bindings — a topologia em código
  ferramentas/MesclarLogs.java            ← a Parte D mexe aqui
  seguranca/FiltroJwt.java                JWT + X-Agencia-Token, nesta ordem

frontend/src/
  modelo/agencias.js                      topologia descoberta do backend (não hardcoded)
  visao/LinhaDoTempo.jsx                  ← a Parte D também mexe aqui

docker-compose.yml                        broker + 3 agências + frontend
.env.local                                RABBITMQ_URL — SEGREDO, gitignored
RESPOSTAS.md                              respostas do roteiro + declaração de uso de IA
evidencias/sprint1/                        18 prints, todos indexados no RESPOSTAS.md
evidencias/sprint2/                        vazia — ver §6
```
