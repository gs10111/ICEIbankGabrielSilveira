package br.pucminas.iceibank.servico;

import java.math.BigDecimal;

public record OrdemDeTransferencia(String chaveIdempotencia, int idOrigem, int idDestino, BigDecimal valor) {
    public boolean mesmaOperacaoQue(OrdemDeTransferencia outra) {
        return idOrigem == outra.idOrigem
                && idDestino == outra.idDestino
                && valor.compareTo(outra.valor) == 0;
    }
}
