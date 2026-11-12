package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que a API devolve. Existe separado da Conta de dominio de proposito:
 * mudar o formato JSON nao deve exigir mexer na regra de negocio, e a Conta
 * nao deve carregar anotacoes de serializacao.
 */
public record ContaResposta(int id, String nomeAluno, BigDecimal saldo, int agencia, List<Integer> vetorAtual) {

    /**
     * Sprint 3, Parte E: toda resposta de conta leva o vetor da agencia. O app guarda o
     * par (saldo, vetor) e devolve o vetor ao sincronizar uma operacao feita offline.
     */
    public static ContaResposta de(Conta conta, int agencia, CarimboVetorial vetorAtual) {
        return new ContaResposta(conta.id(), conta.nome(), conta.saldo(), agencia, vetorAtual.valores());
    }
}
