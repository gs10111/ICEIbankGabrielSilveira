# ICEIBank — Sprint 1

Banco particionado em **três agências independentes**, cada uma responsável por uma
partição de contas (`agência = id_conta % 3`). Toda operação é carimbada com um
**relógio lógico de Lamport**.

> **Há uma falha intencional nesta entrega.** Uma transferência entre agências que
> encontra a agência de destino fora do ar **não reverte o débito**. Isso é exigido
> pelo roteiro (seção 2 e tarefa §8.2), não é defeito: é o problema que o Sprint 4
> resolve com transações distribuídas. Detalhe em [Limitação conhecida](#limitação-conhecida-intencional).

Projeto da disciplina *Laboratório de Desenvolvimento de Aplicações Móveis e
Distribuídas* — PUC Minas / ICEI, Unidade U2.
Aluno: **gabriel silveira** · RA 1466316 (OFFSET **16** → portas 4016/4017/4018).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.5.16 (Maven) |
| Frontend | React 18 + Vite |
| Autenticação | JWT (jjwt) com filtro escrito à mão + BCrypt |
| Persistência | em memória (Sprint 1 não exige banco) |
| Log de eventos | arquivo `.jsonl`, uma linha JSON por evento |

## Arquitetura — MVC

```
controle/     Controllers e DTOs            ← traduz HTTP, nada mais
servico/      ContaService, TransferenciaService, ExtratoConsolidadoService,
              AgenciaRemota (cliente REST das outras agências)
modelo/       Conta, Particionador, RelogioLamport, Carimbo, Evento   ← ZERO Spring
repositorio/  ContaRepositorio, RegistroDeEventos, RegistroDeIdempotencia
seguranca/    FiltroJwt, JwtService, credenciais
config/       AgenciaProperties, beans, carga inicial
```

O **modelo** é a única camada sem anotação de framework — por isso as regras de
negócio testam sem subir Spring. Verificação executável:

```bash
grep -r "org.springframework" agencia/src/main/java/br/pucminas/iceibank/modelo/   # vazio
```

O mesmo vocabulário vale no frontend: `modelo/` · `visao/` · `controle/`.

---

## Como executar

### 1. Backend — as 3 agências

```bash
cd agencia
mvn clean package

# cada agência é o MESMO jar, identificado por variável de ambiente (12-Factor III)
AGENCIA_ID=0 SERVER_PORT=4016 java -jar target/iceibank-agencia-1.0.0.jar
AGENCIA_ID=1 SERVER_PORT=4017 java -jar target/iceibank-agencia-1.0.0.jar
AGENCIA_ID=2 SERVER_PORT=4018 java -jar target/iceibank-agencia-1.0.0.jar
```

Ou, com o script de conveniência, em um terminal só:

```bash
./subir-agencias.sh          # sobe as três em background
./derrubar-agencias.sh       # encerra todas
```

Cada agência cria no boot as contas de demonstração que lhe pertencem:

| Conta | Titular | Cálculo | Agência | Porta | Senha |
|---|---|---|---|---|---|
| 0 | Ana Souza | 0 mod 3 = 0 | 0 | 4016 | `ana123` |
| 1 | Bruno Lima | 1 mod 3 = 1 | 1 | 4017 | `bruno123` |
| 2 | Carla Dias | 2 mod 3 = 2 | 2 | 4018 | `carla123` |
| 3 | Diego Melo | 3 mod 3 = 0 | 0 | 4016 | `diego123` |
| 4 | Ana Souza | 4 mod 3 = 1 | 1 | 4017 | `ana123` |
| 5 | Bruno Lima | 5 mod 3 = 2 | 2 | 4018 | `bruno123` |

Ana (0 e 4) e Bruno (1 e 5) têm contas em **agências diferentes** — é o que torna o
extrato consolidado demonstrável.

### 2. Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

### 3. Linha do tempo unificada (Parte E)

```bash
cd agencia
java -jar target/iceibank-agencia-1.0.0.jar --mesclar-logs data
```

---

## Endpoints

| Método | Rota | Autenticação | O quê |
|---|---|---|---|
| POST | `/auth/login` | pública | devolve o JWT |
| GET | `/status` | pública | health-check: contas, eventos, relógio |
| POST | `/contas` | JWT | abre conta (rejeita se não for desta agência) |
| GET | `/contas/{id}` | JWT | consulta saldo |
| POST | `/contas/{id}/depositar` | JWT | depósito |
| POST | `/contas/{id}/sacar` | JWT | saque |
| POST | `/transferencias` | JWT | transferência local ou entre agências |
| POST | `/contas/{id}/creditar-remoto` | `X-Agencia-Token` | **interna**, agência-a-agência |
| GET | `/contas/{id}/historico` | JWT | **extra 1** — eventos da conta |
| GET | `/eventos` | JWT | eventos da agência (alimenta a linha do tempo) |
| GET | `/extrato-consolidado` | JWT | **extra 3** — soma saldos entre agências |

Idempotência (**extra 2**): envie `Idempotency-Key: <valor>` em `POST /transferencias`.

---

## Configuração (12-Factor III)

Tudo tem default de desenvolvimento no `application.yml` e é sobrescrito por ambiente:

| Variável | Default | O quê |
|---|---|---|
| `AGENCIA_ID` | `0` | identidade desta agência |
| `SERVER_PORT` | `4016` | porta HTTP |
| `AGENCIA_URLS` | `localhost:4016,4017,4018` | malha (muda em containers) |
| `AGENCIA_TOTAL` | `3` | número de agências (divisor da partição) |
| `JWT_SEGREDO` | *(dev)* | chave HMAC — **trocar em produção** |
| `JWT_VALIDADE` | `900` | validade do token, em segundos |
| `AGENCIA_TOKEN` | *(dev)* | segredo das chamadas entre agências |

O **mesmo jar** roda como as três agências sem recompilar. No Sprint 4, o
`docker-compose` só precisa passar `environment:`.

---

## Testes

```bash
cd agencia && mvn test        # 89 testes
```

| Suíte | Testes | O que cobre |
|---|---|---|
| `RelogioLamportTest` | 8 | as 3 regras + concorrência (100 threads em `eventoLocal` e em `aoReceber`) |
| `ParticionadorTest` | 5 | `id % 3`, fronteiras, entradas inválidas |
| `ContaTest` | 6 | invariantes de saldo, `BigDecimal` |
| `ContaServiceTest` | 16 | casos de uso + Lamport aplicado + histórico |
| `TransferenciaServiceTest` | 18 | local, entre agências, **falha conhecida**, idempotência |
| `ExtratoConsolidadoServiceTest` | 5 | soma local + remota, agência fora do ar, `consistente: false` |
| `ContaRepositorioTest` | 4 | persistência em memória |
| `RegistroDeEventosTest` | 3 | formato `.jsonl` |
| `JwtServiceTest` | 3 | algoritmo HS256 fixo, claims, token de outro emissor |
| `ContaControllerTest` | 9 | rotas, códigos HTTP, validação |
| `AutenticacaoTest` | 12 | os 3 cenários da Parte F + login + chamada interna + bypass do token de serviço |

**68 dos 89** rodam **sem subir o Spring** (modelo, serviços e repositórios) e
terminam em menos de um segundo. Só `ContaControllerTest` e `AutenticacaoTest`
levantam o contexto.

---

## Limitação conhecida (intencional)

Se a agência de destino cair no meio de uma transferência entre agências, o débito
**já aplicado não é revertido**. A resposta é **502** dizendo isso explicitamente, e o
evento `TRANSFERENCIA_FALHOU` é registrado com o saldo pós-débito.

Isso é deliberado: é o problema que o **Sprint 4** resolve com transações distribuídas
(2PC ou Saga). Ver `RESPOSTAS.md`, questão 8.3.2.

---

## Documentos

- `RESPOSTAS.md` — todas as questões do roteiro, decisões de design e declaração de uso de IA
- `evidencias/sprint1/` — prints das execuções
