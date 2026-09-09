package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.conta.Conta;

import java.math.BigDecimal;

/**
 * O que a API devolve. Existe separado da Conta de dominio de proposito:
 * mudar o formato JSON nao deve exigir mexer na regra de negocio, e a Conta
 * nao deve carregar anotacoes de serializacao.
 */
public record ContaResposta(int id, String nomeAluno, BigDecimal saldo, int agencia) {

    public static ContaResposta de(Conta conta, int agencia) {
        return new ContaResposta(conta.id(), conta.nome(), conta.saldo(), agencia);
    }
}
