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

_(a responder)_

### 8.3.2 — Reproduza a falha conhecida. O saldo da conta de origem foi revertido? O que isso significa em termos de consistência?

_(a responder)_

### 8.3.3 — Cite, em alto nível, duas formas possíveis de corrigir esse problema (Sprint 4).

_(a responder)_

---

## Parte E — Linha do tempo unificada (seção 10.3)

### 10.3.1 — O relógio garante `A → B ⟹ ts(A) < ts(B)`, mas não a volta. O que isso significa na prática?

_(a responder)_

### 10.3.2 — O relógio de Lamport sozinho bastaria para distinguir com certeza "A e B são concorrentes" de "A aconteceu antes de B"? Por que isso motiva o relógio vetorial?

_(a responder)_

### Observação do passo 3 da tarefa (§10.2) — par de eventos com o mesmo timestamp

_(a responder após gerar a linha do tempo)_

---

## Parte F — Autenticação JWT (seção 11.3)

### Decisão de design: formato das credenciais

_(a responder e justificar)_

### Decisão de design: a chamada interna `creditar-remoto` entre agências deve carregar token?

_(a responder e justificar)_

### 11.3.1 — Diferença entre autenticação e autorização. Sua implementação verifica as duas? Um usuário autenticado consegue sacar de uma conta que não é dele?

_(a responder)_

### 11.3.2 — Por que o servidor não precisa consultar banco para validar a assinatura de um JWT? Implicação sobre escalabilidade.

_(a responder)_

### 11.3.3 — O que aconteceria se a chave secreta de assinatura vazasse?

_(a responder)_

---

## Parte G — Frontend (seção 12.3)

### 12.3.1 — Como o frontend "lembra" de reenviar o token a cada requisição?

_(a responder)_

### 12.3.2 — O que acontece se o token expirar no meio de uma operação? A interface avisa?

_(a responder)_

### 12.3.3 — No seu frontend, onde ficam o M, o V e o C?

_(a responder)_

---

## Funcionalidade adicional (seção 2.1)

**Escolhida:** idempotência de transferências.

_(descrever o que faz e por que foi escolhida — evidência em `evidencias/sprint1/funcionalidade-adicional.png`)_

---

## Declaração de uso de IA

_(exigência do roteiro — descrever como ferramentas de IA foram utilizadas)_
