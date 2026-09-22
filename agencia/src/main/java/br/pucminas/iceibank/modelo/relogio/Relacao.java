package br.pucminas.iceibank.modelo.relogio;

/**
 * A relacao causal entre dois eventos, segundo seus carimbos vetoriais.
 *
 * Sao QUATRO respostas, nao duas — e e por isso que `Comparable` seria o contrato
 * errado aqui (decisao ja registrada no Sprint 1, RESPOSTAS.md 10.3.2). Comparable
 * promete ordem TOTAL: para quaisquer dois elementos, um vem antes do outro. O
 * relogio vetorial da ordem PARCIAL: existem pares em que nenhum vem antes, porque
 * os eventos simplesmente nao se influenciaram.
 */
public enum Relacao {
    /** Os dois carimbos sao identicos — o mesmo ponto no tempo logico. */
    IGUAIS,
    /** O primeiro aconteceu-antes do segundo: existe cadeia causal de um ao outro. */
    ANTES,
    /** O primeiro aconteceu-depois do segundo. */
    DEPOIS,
    /** Nenhum causou o outro. Lamport, sozinho, nunca consegue afirmar isto. */
    CONCORRENTES
}
