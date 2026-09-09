# RESPOSTAS — ICEIBank Sprint 1

**Aluno:** gabriel silveira
**Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas — U2
**Linguagem escolhida:** Java 21 + Spring Boot 3.5.16 (Maven)
**Arquitetura:** Ports & Adapters (hexagonal) — `dominio` → sem dependência de framework; `aplicacao` → casos de uso e portas; `infra` → adapters

---

## Parte B — Relógio de Lamport (seção 6.4)

### 6.4.1 — Por que `max(contador_local, timestampRecebido) + 1` ao receber, em vez de adotar o timestamp recebido diretamente?

6.4.1: Para proteger a ordem dos processos , pois a agencia pode ter recebido um 7 ja tendo um processo carimbado com 7. Agora existem dois eventos com carimbo 7: o envio e o recebimento. Mas o envio causou o recebimento

- **O `max` protege a ordem LOCAL (dentro do processo).** Se a agência está no contador 10 e simplesmente adotasse o timestamp 3 recebido, o próximo evento local carimbaria 4 — mas essa agência **já emitiu** os carimbos 4, 5, …, 10. Existiriam dois eventos diferentes com o mesmo carimbo dentro do mesmo processo, e o relógio teria **retrocedido**. Relógio lógico nunca pode andar para trás.
  → Teste que prova: `aoReceberCarimboMenorNaoRetrocede` (contador em 10, recebe 3, resultado 11).

- **O `+1` protege a ordem CAUSAL (entre processos).** Sem ele, o envio (na agência de origem) e o recebimento (na agência de destino) ficariam com o mesmo carimbo. Mas o envio **causou** o recebimento, então precisa ser estritamente menor. Carimbos iguais significam "não sei ordenar", o que seria falso nesse caso.
  → Teste que prova: `aoReceberCarimboIgualDesempata` (contador em 5, recebe 5, resultado 6).

Ambas as metades foram verificadas por **teste de mutação manual**: removendo o `Math.max`, o teste do caso menor fica vermelho; removendo o `+ 1`, os três testes de `aoReceber` ficam vermelhos.

### 6.4.2 — Se a Agência 0 está no evento 10 e recebe uma mensagem com timestamp 3, qual o novo valor do contador? O que isso implica sobre agências rápidas versus lentas?

6.4.2: Contador em 10, aoReceber(carimbo 3) → 11 (o local é maior — o max protege),

- A agência **rápida** (contador alto) praticamente **ignora** carimbos baixos: recebendo 3 estando em 10, apenas avança para 11. Um vizinho lento não consegue atrasá-la.
- A agência **lenta** (contador baixo) **salta** ao receber um carimbo alto: estando em 2 e recebendo 7, pula direto para 8 — seis "tiques" de uma vez.
- Consequência: o relógio de cada agência anda no ritmo do **vizinho mais rápido com quem ela se comunica**. O contador de Lamport não mede tempo de parede nem volume de trabalho realizado — mede **posição na cadeia causal**.

**Observação sobre concorrência (limitação conhecida da suíte de testes):** o teste `eventoLocalEhSeguroSobConcorrencia` (100 threads simultâneas) expôs uma condição de corrida real — `contador++` não é atômico, e duas threads chegaram a produzir o mesmo carimbo (`expected: <100> but was: <99>`). Corrigido com `synchronized` em `incrementar()` e `aoReceber()`. O método `aoReceber()` tem exatamente a mesma corrida (ler-modificar-escrever sobre `contador`), mas foi protegido **por inspeção do código**, sem teste dedicado — a suíte cobre apenas `eventoLocal()`.

---

## Parte D — Transferências (seção 8.3)

### 8.3.1 — Por que a transferência local não precisa de `aoEnviar()`/`aoReceber()`, enquanto a transferência entre agências precisa?

Porque as regras 2 e 3 de Lamport existem **para sincronizar relógios diferentes**, e na transferência local só existe **um** relógio.

Na transferência dentro da mesma agência, débito e crédito acontecem no mesmo processo, sobre o mesmo contador. A ordem entre eles já é garantida por dois `eventoLocal()` sucessivos (carimbos 1 e 2 no teste `geraDoisEventosLocais`): o processo é sequencial por definição, não há nada a sincronizar. Usar `aoEnviar`/`aoReceber` ali seria fingir que houve uma mensagem que nunca existiu.

Na transferência entre agências existe uma **mensagem real** cruzando processos:
- a origem faz `aoEnviar()` e anexa o carimbo à requisição (`TransferenciaService.transferirEntreAgencias`);
- o destino faz `aoReceber(carimbo)` ao processar `/creditar-remoto` (`TransferenciaService.creditarRemoto`).

É esse par que propaga a relação "aconteceu-antes" **entre** processos. Sem ele, os dois relógios evoluiriam de forma totalmente independente e a linha do tempo unificada não conseguiria afirmar que o débito precedeu o crédito.

Evidência no log: o débito na agência 0 saiu com carimbo 5, o envio consumiu o 6, e o crédito remoto na agência 1 — que estava em 1 — saiu com **7 = max(1, 6) + 1**. O salto de 1 para 7 é a causalidade viajando pela rede.

### 8.3.2 — Reproduza a falha conhecida. O saldo da conta de origem foi revertido? O que isso significa em termos de consistência?

**Não foi revertido.** Reprodução (evidência em `evidencias/sprint1/falha-conhecida.png`):

- saldo da conta 0 antes: **R$ 35,00**
- agência 1 derrubada (`fuser -k 4017/tcp`)
- `POST /transferencias {idOrigem:0, idDestino:1, valor:20}` → **HTTP 502**
- saldo da conta 0 depois: **R$ 15,00**

O log da agência 0 mostra a sequência:
```
TRANSFERENCIA_DEBITO   Lamport  9   {valor:20, idDestino:1, idOrigem:0}
TRANSFERENCIA_FALHOU   Lamport 11   {erro:"Connection refused", saldoOrigemAposDebito:15.00}
```
(o carimbo 10 foi consumido pelo `aoEnviar()` da mensagem que nunca chegou.)

**O que isso significa:** o sistema perdeu **atomicidade**. A transferência é logicamente uma operação só, mas é executada como duas escritas em processos diferentes, sem nada em volta que garanta "as duas ou nenhuma". Entre o débito e o crédito existe uma janela em que o dinheiro não está em conta nenhuma.

Consequência prática: a soma dos saldos das três agências deixa de ser invariante. Não é um saldo "errado" numa conta — é o **sistema inteiro** ficando inconsistente, e nenhuma agência sozinha consegue perceber isso, porque cada uma só enxerga a própria partição.

A escolha aqui foi **falhar de forma visível** em vez de esconder: o 502 diz explicitamente que o débito foi aplicado e não revertido, e o evento `TRANSFERENCIA_FALHOU` grava o saldo pós-débito, deixando rastro para uma reconciliação futura.

### 8.3.3 — Cite, em alto nível, duas formas possíveis de corrigir esse problema (Sprint 4).

**1. Two-Phase Commit (2PC) — atomicidade por bloqueio.**
Um coordenador pergunta a todos os participantes "você consegue commitar?" (fase de preparação); cada um reserva os recursos e responde sim/não sem efetivar. Só se **todos** disserem sim o coordenador manda commitar. Aqui: a agência de origem reservaria o valor e a de destino confirmaria que a conta existe e aceita o crédito, antes de qualquer saldo mudar.
*Custo:* é bloqueante. Se o coordenador cair depois do "prepare", os participantes ficam com recursos travados esperando indefinidamente. Sacrifica disponibilidade por consistência.

**2. Saga — atomicidade por compensação.**
A transferência vira uma sequência de passos locais, cada um com uma **transação compensatória**. Débito e crédito são commitados separadamente; se o crédito falhar, dispara-se o compensador do débito (um estorno), que é uma operação de negócio nova, não um rollback técnico.
*Custo:* aceita inconsistência **temporária** — existe uma janela real em que o dinheiro está "em trânsito". Em compensação não bloqueia ninguém e sobrevive melhor a falhas parciais.

**Como o código já está preparado:** a chamada remota está atrás da porta `AgenciaRemota`, então trocar o protocolo é escrever um adapter novo, sem tocar em `TransferenciaService`. E a **idempotência** (funcionalidade adicional 2) já implementada é pré-requisito de Saga: passos compensatórios são reexecutados sob falha, e sem idempotência a retentativa aplicaria a operação duas vezes.

---

## Parte E — Linha do tempo unificada (seção 10.3)

### 10.3.1 — O relógio garante `A → B ⟹ ts(A) < ts(B)`, mas não a volta. O que isso significa na prática?

Significa que `ts(A) < ts(B)` **não permite concluir nada**. Olhando dois eventos com carimbos 5 e 9, existem duas explicações possíveis e o relógio não distingue:

1. A causou B (existe uma cadeia de mensagens ligando os dois), ou
2. A e B são **concorrentes** — aconteceram em processos diferentes sem nenhuma comunicação entre eles, e os contadores simplesmente estavam nesses valores.

A implicação é direta: a linha do tempo produzida pelo `mesclar-logs` é uma ordenação **plausível**, não a ordem real. Ela nunca vai contradizer a causalidade (isso o relógio garante), mas inventa ordem onde não existe nenhuma.

O que se pode afirmar com segurança é apenas a **contrapositiva**: se `ts(A) >= ts(B)`, então A definitivamente **não** causou B. É uma ferramenta para excluir causalidade, não para provar.

### 10.3.2 — O relógio de Lamport sozinho bastaria para distinguir com certeza "A e B são concorrentes" de "A aconteceu antes de B"? Por que isso motiva o relógio vetorial?

**Não basta.** Um único inteiro colapsa a história de todos os processos numa dimensão só, e nessa projeção a informação de "quem sabia de quem" se perde.

O relógio vetorial resolve guardando **um contador por processo**: cada agência mantém `[c0, c1, c2]`, incrementa a própria posição nos eventos locais e, ao receber uma mensagem, faz o máximo **posição a posição**. O vetor deixa de ser um número e passa a ser um resumo de *tudo que aquele processo já soube de cada um dos outros*.

Com isso a comparação passa a ter três resultados em vez de dois:
- `V(A) < V(B)` em todas as posições e estritamente menor em ao menos uma → **A aconteceu antes de B**
- o inverso → B aconteceu antes de A
- nenhum dos dois (cada vetor é maior em alguma posição) → **concorrentes**, com certeza

É por isso que `Comparable` seria a abstração errada para o carimbo (ver a decisão de design abaixo): `Comparable` promete ordem **total**, e o vetorial só oferece ordem **parcial**. Uma interface que promete o que a implementação não pode cumprir viola o princípio de substituição de Liskov — e essa foi a razão de `Carimbo` ter nascido sem `Comparable` já no Sprint 1.

### Observação do passo 3 da tarefa (§10.2) — par de eventos com o mesmo timestamp

A ferramenta `--mesclar-logs` encontrou **dois** empates numa execução comum (evidência em `evidencias/sprint1/linha-do-tempo.png`):

**Empate em Lamport 1**
```
agencia-0  CRIAR_CONTA  (hora de parede 12:50:00.541)
agencia-1  CRIAR_CONTA  (hora de parede 12:50:01.452)
```
São **concorrentes**. Cada agência subiu e criou suas contas iniciais sem trocar mensagem com a outra; os dois contadores foram de 0 para 1 de forma independente. Nenhum causou o outro.

**Empate em Lamport 7**
```
agencia-1  TRANSFERENCIA_CREDITO_REMOTO  (12:50:01.872)
agencia-0  TRANSFERENCIA_DEBITO          (12:50:01.959)
```
Também concorrentes — e este é o caso mais interessante, porque *parece* relacionado: os dois envolvem transferência. Mas o crédito remoto na agência 1 veio de uma transferência **anterior** (a de R$ 25), enquanto o débito na agência 0 é de uma transferência **posterior** (a de R$ 10). Não existe cadeia causal entre eles.

**Comparando com a hora de parede:** neste caso as duas ordens coincidiram (a hora física respeitou a ordem de inserção), mas isso é **coincidência de as três agências rodarem na mesma máquina**, compartilhando o mesmo relógio de hardware. Com as agências em máquinas distintas, cujos relógios divergem por milissegundos ou segundos, a ordem por hora de parede poderia inverter-se sem que nada de errado tivesse acontecido. É exatamente por isso que `horaParede` é gravado no evento mas **nenhuma decisão do sistema o consulta** — ele existe só para esta comparação.

---

## Parte F — Autenticação JWT (seção 11.3)

### Decisão de design: formato das credenciais

**Escolha: `<número da conta, senha>`.** `POST /auth/login` recebe `{"idConta": 0, "senha": "ana123"}`.

**Justificativa.** A conta já é a identidade natural deste sistema: ela é particionada por `id % 3`, aparece em toda operação e é o que o usuário conhece. Criar uma entidade "usuário" separada exigiria um segundo modelo de identidade e uma tabela de vínculo usuário↔conta, sem acrescentar nada ao que o sprint estuda (partição, relógio lógico, atomicidade).

**Onde a credencial mora:** em `infra/seguranca/`, **fora do domínio**. A classe `Conta` cuida de saldo e invariantes de dinheiro; senha é preocupação de autenticação. Se a senha morasse na `Conta`, mudar a política de senha exigiria editar a classe que guarda dinheiro — violação direta do princípio de responsabilidade única.

**Armazenamento:** BCrypt (`spring-security-crypto`), nunca texto puro. BCrypt é deliberadamente lento, o que encarece força bruta, e usa salt por senha.

**Detalhe de segurança:** login com conta inexistente devolve **401 genérico** (`"credenciais invalidas"`), igual a senha errada. Dizer "conta não existe" permitiria a um atacante enumerar contas válidas antes de tentar senhas. Já conta de **outra agência** devolve **400** com a mensagem apontando a agência correta — aí não é falha de credencial, é porta de entrada errada, e esconder isso só atrapalharia o usuário legítimo.

**Expiração:** 15 minutos (`JWT_VALIDADE=900`), configurável por ambiente.

### Decisão de design: a chamada interna `creditar-remoto` entre agências deve carregar token?

**Sim, mas NÃO o JWT do usuário.** Ela usa um **segredo de serviço compartilhado**, enviado no header `X-Agencia-Token` e configurado por ambiente (`AGENCIA_TOKEN`).

Três razões:

**1. Um JWT identifica uma pessoa; quem chama `/creditar-remoto` é um processo.** Repassar o token do usuário faria a agência de destino acreditar que *a pessoa* está pedindo o crédito, quando na verdade é a agência de origem agindo em nome dela. Isso é o problema clássico do **confused deputy**: um componente privilegiado usa a autoridade de outro sem que ninguém tenha autorizado esse repasse.

**2. Tempo de vida incompatível.** O token do usuário expira em 15 minutos. A comunicação entre agências precisa funcionar independentemente de haver alguém logado — uma retentativa ou uma operação em lote não pode falhar porque a sessão de uma pessoa acabou.

**3. Escopo diferente.** O JWT do usuário deveria autorizar operações *daquela conta*. `/creditar-remoto` credita uma conta que **não é** a do chamador — usar o token dele para isso confundiria os dois níveis de autorização.

**Implementação** (`FiltroJwt`): se o header `X-Agencia-Token` bate com o segredo configurado, a requisição passa como chamada de serviço, sem exigir JWT. Isso vale para qualquer rota, porque o extrato consolidado também precisa **ler** contas de outras agências.

**Limitação assumida:** é um segredo compartilhado (autenticação simétrica), não um certificado por agência. Ele identifica "alguém do cluster", não "a agência 1 especificamente". Para produção o certo seria mTLS ou um token por serviço com escopo. Para o Sprint 1 isso já separa corretamente as duas identidades — pessoa e processo — que é o ponto conceitual.

### 11.3.1 — Diferença entre autenticação e autorização. Sua implementação verifica as duas? Um usuário autenticado consegue sacar de uma conta que não é dele?

**Autenticação** responde *"quem é você?"* — provar identidade. **Autorização** responde *"você pode fazer isto?"* — verificar permissão sobre um recurso específico. São independentes: dá para estar autenticado e não autorizado.

**Minha implementação verifica apenas AUTENTICAÇÃO.** O `FiltroJwt` valida a assinatura e a expiração do token e libera a requisição. Ele coloca o usuário autenticado num atributo (`FiltroJwt.ATRIBUTO_AUTENTICADO`), mas **nenhum controller compara** o `subject` do token com o id da conta que está sendo operada.

**Resposta direta à pergunta: sim, consegue.** Ana (conta 0) faz login, recebe um token, e pode chamar `POST /contas/3/sacar` — a conta do Diego, que também está na agência 0. O filtro só confere que ela tem um token válido; ninguém confere que a conta 3 é dela.

**Isto é uma falha de segurança real**, e do tipo mais comum em APIs: *Broken Object Level Authorization* (OWASP API1). Está documentada aqui em vez de escondida.

**Como seria corrigido:** no `ContaController`, comparar o `idConta` do token com o `{id}` do path e devolver **403 Forbidden** quando divergirem — 403, não 401, porque a identidade é válida; o que falta é permissão. A regra de "quem pode operar qual conta" pertenceria a uma camada de autorização, e — pelo mesmo raciocínio da idempotência — o lugar natural seria um **decorator** sobre os casos de uso, não um `if` espalhado por cada controller.

Não foi implementado porque o roteiro pede autenticação (Parte F) e o escopo do sprint já inclui três funcionalidades adicionais. Fica como dívida consciente e documentada.

### 11.3.2 — Por que o servidor não precisa consultar banco para validar a assinatura de um JWT? Implicação sobre escalabilidade.

Porque a validação é **matemática, não uma busca**. O token tem três partes (`header.payload.assinatura`), e a assinatura é `HMAC-SHA(header + payload, chave_secreta)`. Para validar, o servidor recalcula o HMAC com a chave que já tem em memória e compara com a assinatura recebida. Se bate, duas coisas ficam provadas de uma vez: o conteúdo não foi adulterado, e quem emitiu conhecia a chave.

O `payload` já **carrega** os dados (`sub`, `nome`, `agencia`, `exp`). Não é uma chave para procurar informação em outro lugar — é a informação, assinada.

**Implicação sobre escalabilidade — a comparação que importa:**

| | Sessão em memória | JWT |
|---|---|---|
| onde mora o estado | no servidor | no cliente |
| validar exige | consultar o armazenamento de sessões | só CPU |
| escalar horizontalmente | precisa de sessão pegajosa ou Redis compartilhado | qualquer instância atende qualquer requisição |
| custo por requisição | uma ida à rede/disco | microssegundos de HMAC |

No ICEIBank isso é concreto: as **três agências compartilham o mesmo segredo**, então um token emitido pela agência 0 é aceito pela 1 e pela 2 sem que elas troquem uma única mensagem. Com sessão em memória, cada agência precisaria de um armazenamento de sessões comum — mais um componente compartilhado, exatamente o que uma arquitetura particionada tenta evitar.

**O preço:** o token não pode ser revogado antes de expirar. Como o servidor não consulta nada, ele não tem onde marcar "este token não vale mais". Logout no cliente só apaga o token localmente — se alguém tiver uma cópia, ela funciona até o `exp`. É o trade-off direto de ser stateless, e a mitigação usual é manter a expiração curta (aqui, 15 minutos) e usar refresh tokens.

### 11.3.3 — O que aconteceria se a chave secreta de assinatura vazasse?

**Comprometimento total da autenticação.** Quem tem a chave pode **forjar** tokens válidos — não precisa roubar nenhum token existente nem descobrir senha alguma.

Com a chave, um atacante monta o payload que quiser e assina:
```json
{"sub": "0", "nome": "Ana Souza", "agencia": 0, "exp": <daqui a um ano>}
```
Esse token é **indistinguível** de um legítimo, porque a validação é só a verificação da assinatura. E como o sistema é stateless, não existe nenhuma consulta que pudesse desmentir o token.

No ICEIBank o dano é agravado por duas escolhas:
- as **três agências compartilham a mesma chave**, então o vazamento compromete o sistema inteiro, não uma agência;
- o atacante escolhe o próprio `exp`, então o token forjado dura o que ele quiser.

**Resposta ao incidente:** trocar a chave. Isso invalida **todos** os tokens de uma vez (os legítimos também — todo mundo é deslogado), e é justamente por isso que funciona.

**Como o projeto tenta reduzir o risco:**
- a chave vem de variável de ambiente (`JWT_SEGREDO`), com o default no `application.yml` marcado como **exclusivo de desenvolvimento** — 12-Factor III;
- `.env` está no `.gitignore` desde o primeiro commit, antes de existir qualquer segredo (segredo commitado permanece no histórico mesmo depois de deletado);
- `SegurancaProperties` **valida no boot** que o segredo tem ≥ 32 caracteres; a jjwt recusa chave curta para HS256, e a validação falha cedo em vez de na primeira requisição.

**O que faltaria em produção:** rotação periódica de chave com `kid` no header (permitindo aceitar a chave antiga por uma janela), gerenciamento por um cofre (AWS Secrets Manager, Vault) em vez de variável de ambiente, e assinatura assimétrica (RS256) — assim as agências verificariam com a chave **pública** e só o emissor teria a privada, reduzindo drasticamente a superfície de vazamento.

---

## Parte G — Frontend (seção 12.3)

### 12.3.1 — Como o frontend "lembra" de reenviar o token a cada requisição?

Um **único módulo** (`src/modelo/api.js`) sabe da existência do token. Nenhuma tela monta header de autenticação.

O fluxo:
1. `POST /auth/login` devolve `{token, expiraEmSegundos, ...}`.
2. `guardarSessao()` grava em `localStorage` (`iceibank.token` e `iceibank.sessao`).
3. Toda chamada passa pela função interna `requisitar()`, que lê o token e injeta `Authorization: Bearer <token>` antes do `fetch`.

```js
async function requisitar(idAgencia, caminho, opcoes = {}) {
  const cabecalhos = { 'Content-Type': 'application/json', ...(opcoes.headers ?? {}) }
  const token = tokenAtual()
  if (token) cabecalhos.Authorization = `Bearer ${token}`
  ...
}
```

É o padrão **interceptor**: as telas chamam `api.depositar(...)` e não sabem que autenticação existe. A vantagem prática é ter um só lugar para mudar — se amanhã o token virar cookie `HttpOnly`, muda-se `api.js` e nenhuma tela é tocada.

`localStorage` (e não memória) mantém a sessão através de recarregamentos de página. O trade-off é conhecido: `localStorage` é acessível por JavaScript, então é vulnerável a XSS. Um cookie `HttpOnly` + `SameSite` seria mais seguro; foi escolhido `localStorage` por ser o que o roteiro sugere e por manter o backend stateless sem lidar com CSRF.

### 12.3.2 — O que acontece se o token expirar no meio de uma operação? A interface avisa?

**Sim, avisa em tela — em dois momentos, antes e depois.**

**Antes (aviso preventivo).** A barra do produto mostra uma contagem regressiva do token (`token 154s`), calculada por `useAutenticacao` a partir do `expiraEmSegundos` do login. Abaixo de 20 segundos o número muda para `--color-accent-800`, avisando visualmente antes de qualquer erro acontecer.

**Depois (tratamento do 401).** Se a expiração pegar uma requisição em andamento, o backend devolve 401 e o `App.jsx` intercepta **antes** que o erro genérico apareça:

```js
const tratar = useCallback((erro) => {
  if (erro instanceof ErroDaApi && erro.http === 401) {
    doErro(new ErroDaApi(401, 'Seu token expirou. Faça login novamente para continuar.'))
    setTimeout(sair, 2500)
    return
  }
  doErro(erro)
}, [doErro, sair])
```

A pessoa vê a faixa de alerta com o título **"Sessão expirada"**, o código **HTTP 401** e a frase em português explicando o que fazer. Dois segundos e meio depois a sessão é limpa e a tela de login volta — tempo suficiente para ler a mensagem, sem deixar a pessoa presa numa tela que não funciona mais.

**Não é um erro genérico e não fica só no console.** O `useAlerta` traduz cada código HTTP num título legível (`401 → "Sessão expirada"`, `502 → "Falha entre agências"`, `503 → "Agência fora do ar"`), e a faixa de alerta é renderizada em todas as telas.

Há ainda o botão **"Expirar token"** na barra, que invalida o token na hora — existe justamente para produzir o print `auth-token-expirado.png` sem esperar 15 minutos.

### 12.3.3 — No seu frontend, onde ficam o M, o V e o C?

A separação é **explícita na estrutura de pastas**, não implícita:

```
frontend/src/
├── modelo/            ← MODEL
│   ├── api.js         acesso à API das 3 agências + gestão do token
│   ├── agencias.js    malha, portas e a regra de partição (id % 3)
│   └── formato.js     moeda pt-BR, hora de parede, rótulos de evento
│
├── visao/             ← VIEW
│   ├── Login.jsx, Painel.jsx, Movimentacao.jsx, Transferencia.jsx,
│   │   Historico.jsx, Extrato.jsx, LinhaDoTempo.jsx, Layout.jsx
│   └── componentes/Base.jsx   (Blueprint, Alerta, Campo, Segmentado, Tag, ícones)
│
└── controle/          ← CONTROLLER
    ├── useAutenticacao.js   sessão, token, contagem regressiva
    └── useAlerta.js         traduz erro HTTP em mensagem de tela
        + App.jsx            orquestra estado, chamadas e navegação
```

**Model** — sabe falar com o servidor e como os dados se parecem. Não sabe que React existe. `agencias.js` inclusive **duplica a regra de partição** do backend, de propósito: permite recusar no cliente uma operação sobre conta de outra agência com uma mensagem melhor, sem ida ao servidor. A validação é repetida no backend, porque validação de cliente é conveniência, nunca segurança.

**View** — componentes de apresentação. **Nenhum deles chama `fetch`.** Recebem dados e callbacks por props e devolvem JSX. É por isso que `Movimentacao.jsx` serve para depósito e saque: a diferença é uma prop.

**Controller** — os hooks e o `App.jsx`. É onde mora o estado, onde as chamadas ao Model acontecem, e onde o erro HTTP vira mensagem em português.

**Ficou claro ou misturado?** Ficou claro nas fronteiras, com uma concessão honesta: o `App.jsx` acumula o papel de controller de todas as telas e passou de 200 linhas. Se o projeto crescesse, o caminho natural seria um hook de controller por tela (`usePainel`, `useTransferencia`), mantendo o `App` só com roteamento. Está anotado como dívida.

Vale notar que essa é **a mesma separação do backend**, aplicada de novo: `modelo` ↔ `dominio`, `visao` ↔ `infra/web`, `controle` ↔ `aplicacao`. MVC e Ports & Adapters não competem — o MVC é como a camada de apresentação se organiza dentro do adapter de entrada.

---

## Funcionalidades adicionais (seção 2.1)

Foram implementadas **três** (o roteiro exige pelo menos uma).

---

### 1. Histórico de transações por conta

**O que faz:** `GET /contas/{id}/historico?limite=N` devolve os últimos eventos que envolvem aquela conta, do mais recente para o mais antigo, cada um com seu carimbo de Lamport, tipo, detalhes e hora de parede.

**Por que escolhi:** é a contrapartida natural do registro de eventos que o sprint já exige. O `.jsonl` era escrito e nunca lido pela aplicação — o histórico transforma o log de artefato de depuração em funcionalidade de produto.

**Decisão de design:** criei uma **porta separada** para leitura (`ConsultaEventos`) em vez de acrescentar um método a `RegistroEventos`. Motivo: quem escreve (`ContaService`, `TransferenciaService`) não precisa conhecer a operação de leitura, e vice-versa — Interface Segregation. É o embrião da separação entre caminho de escrita e de leitura (CQRS). `RegistroEventosJsonl` implementa as duas interfaces; quem consome enxerga só a que precisa.

**Filtragem:** o adapter procura o id da conta nos campos `id`, `idConta`, `idOrigem` e `idDestino` dos detalhes, então uma transferência aparece no histórico das **duas** contas envolvidas.

**Testes:** `ContaServiceTest` (ordem, limite, conta inexistente) e `ContaControllerTest.historicoDaConta`.

---

### 2. Idempotência de transferências

**O que faz:** o cliente envia `Idempotency-Key: <valor>` (header, convenção de mercado) ou `chaveIdempotencia` no corpo. A primeira requisição executa; reenvios da **mesma chave** devolvem o recibo original com `reenvio: true`, **sem debitar de novo**.

**Por que escolhi:** é o extra com maior conteúdo de sistemas distribuídos. Reenvio não é hipótese acadêmica — é o que acontece quando a resposta se perde e o cliente tenta de novo, exatamente o cenário da falha da Parte D. E é **pré-requisito de Saga**: passos compensatórios são reexecutados sob falha, e sem idempotência a retentativa aplicaria a operação duas vezes. Prepara o Sprint 4 de verdade.

**Decisão de design — Decorator, não `if`:**
```java
new TransferenciaIdempotente( new TransferenciaService(...), registroIdempotencia )
```
`TransferenciaService` **nunca foi editado** para ganhar idempotência: comportamento novo sem modificar código existente é Open/Closed literal. E cada classe tem um motivo para mudar — uma sabe transferir, a outra sabe deduplicar.

**Quatro sutilezas resolvidas:**
- **Guarda o recibo, não um booleano.** O reenvio devolve a *mesma* resposta; senão o cliente veria comportamentos diferentes para a mesma requisição.
- **A chave vem do cliente.** Se o servidor a gerasse, cada reenvio teria chave nova e a deduplicação não faria nada.
- **Corrida resolvida na porta.** A interface expõe `executarUmaVez(chave, ordem, operacao)` em vez de `consultar` + `guardar` — com dois métodos, duas requisições simultâneas passariam ambas pela consulta antes de qualquer gravação. O adapter usa `ConcurrentHashMap.computeIfAbsent`, que é atômico por chave.
- **Mesma chave com dados diferentes → 409.** Devolver calado o recibo de outra operação seria pior que gritar.
- **Falha não é memorizada.** Se a operação lança, nada é gravado: a retentativa é permitida.

**Testes:** `TransferenciaServiceTest.Idempotencia` — 5 casos (reenvio não debita duas vezes, chaves diferentes aplicam duas vezes, sem chave aplica sempre, conflito de payload, falha não memorizada).

---

### 3. Extrato consolidado

**O que faz:** `GET /extrato-consolidado?contas=0,4` soma os saldos de várias contas do mesmo titular, **mesmo em agências diferentes**. Para cada conta, a agência decide pela regra de partição se busca localmente ou faz uma chamada remota.

Demonstração real: Ana Souza tem a conta 0 (agência 0) e a conta 4 (agência 1). O extrato devolve `total: 1250.00` — R$ 1.000 da agência 0 mais R$ 250 da agência 1.

**Por que escolhi:** é a primeira **leitura distribuída** do sistema, e ela expõe um limite que nenhuma outra parte do sprint mostra.

**O limite que ele revela — e que está no código:** o total **não é um snapshot atômico**. As agências são consultadas uma a uma e uma transferência pode acontecer entre duas leituras, produzindo um total que nunca existiu em nenhum instante. Por isso o resultado carrega o campo `consistente`, e cada conta carrega `disponivel`: se uma agência não responder, o extrato devolve um total **rotulado como parcial** em vez de um número errado sem aviso.

Isso é a leitura sofrendo do mesmo problema que a escrita sofre na Parte D — e é a mesma família de solução (snapshot consistente, leitura em duas fases) que o Sprint 4 vai discutir.

**Decisão de design:** outra porta separada, `ConsultaContaRemota`, distinta de `AgenciaRemota` (que escreve). Quem só transfere não precisa poder ler remotamente; quem só consolida não precisa poder creditar.

**Evidência:** `evidencias/sprint1/funcionalidade-adicional.png`

---

## Declaração de uso de IA

_(PREENCHER — este texto é um rascunho; ajuste para descrever com precisão o que de fato aconteceu, porque é você quem assina.)_

Utilizei o Claude (Anthropic) como apoio ao longo do sprint, nos seguintes papéis:

- **Explicação conceitual e revisão socrática** na primeira metade do projeto: o relógio de Lamport, o particionamento e o modelo de domínio (`Conta`, `Particionador`, `RelogioLamport`) foram escritos por mim, com a IA revisando cada ciclo de TDD, apontando erros e explicando o porquê — incluindo o bug de fronteira em `Particionador` (`> 1` onde devia ser `> 0`), a condição de corrida no contador do relógio e a armadilha de imutabilidade do `BigDecimal`.
- **Geração de código sob orientação** na segunda metade (camada web, transferências, JWT, frontend), por restrição de prazo, sempre seguindo decisões de arquitetura que eu havia tomado antes: Ports & Adapters, Decorator para idempotência, filtro JWT escrito à mão em vez de Spring Security, carimbo opaco em vez de `int`.
- **Revisão de texto** deste documento.

Sou capaz de explicar e defender qualquer trecho entregue. As decisões de design registradas neste arquivo — por que o carimbo não implementa `Comparable`, por que a idempotência é um decorator, por que a chamada entre agências não carrega JWT de usuário, por que a falha da Parte D não é revertida — foram discutidas e decididas por mim antes de virarem código.

**Limitação conhecida e documentada por escolha própria:** a implementação verifica autenticação mas não autorização por recurso (ver questão 11.3.1). Optei por registrar isso em vez de omitir.
