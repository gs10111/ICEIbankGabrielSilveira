package br.pucminas.iceibank.servico;

import java.math.BigDecimal;

public record Recibo(String mensagem, boolean local, int idOrigem, int idDestino,
                     BigDecimal valor, BigDecimal saldoOrigem, boolean reenvio) {
    public Recibo comoReenvio() {
        return new Recibo(mensagem, local, idOrigem, idDestino, valor, saldoOrigem, true);
    }
}
