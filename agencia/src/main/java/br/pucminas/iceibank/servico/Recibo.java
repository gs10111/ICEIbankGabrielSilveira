package br.pucminas.iceibank.servico;

import java.math.BigDecimal;

public record Recibo(String mensagem, boolean local, int idOrigem, int idDestino,
                     BigDecimal valor, BigDecimal saldoOrigem, boolean reenvio) {

    /** Copia marcada como reenvio — o cliente ve que a operacao ja tinha sido aplicada. */
    public Recibo comoReenvio() {
        return new Recibo(mensagem, local, idOrigem, idDestino, valor, saldoOrigem, true);
    }
}
