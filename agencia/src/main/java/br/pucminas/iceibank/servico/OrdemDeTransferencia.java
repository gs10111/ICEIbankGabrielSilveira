package br.pucminas.iceibank.servico;

import java.math.BigDecimal;

/**
 * @param chaveIdempotencia identificador unico da operacao, gerado pelo CLIENTE.
 *        Se fosse gerado aqui, cada reenvio teria chave nova e a deduplicacao
 *        nao faria nada. Nulo = cliente abriu mao da garantia.
 */
public record OrdemDeTransferencia(String chaveIdempotencia, int idOrigem, int idDestino, BigDecimal valor) {

    /** Duas ordens sao "a mesma operacao" se os dados de negocio batem (a chave nao conta). */
    public boolean mesmaOperacaoQue(OrdemDeTransferencia outra) {
        return idOrigem == outra.idOrigem
                && idDestino == outra.idDestino
                && valor.compareTo(outra.valor) == 0;
    }
}
