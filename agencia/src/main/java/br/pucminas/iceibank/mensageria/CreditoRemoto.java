package br.pucminas.iceibank.mensageria;

import java.math.BigDecimal;
import java.util.List;

public record CreditoRemoto(
        int idConta,
        BigDecimal valor,
        List<Integer> vetorEnvio,
        int origemAgencia) {
    public String identidade() {
        return "agencia-" + origemAgencia + vetorEnvio;
    }
}
