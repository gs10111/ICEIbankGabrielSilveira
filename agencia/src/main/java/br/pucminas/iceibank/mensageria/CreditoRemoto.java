package br.pucminas.iceibank.mensageria;

import java.math.BigDecimal;
import java.util.List;

/**
 * O corpo da mensagem que uma agencia publica para outra creditar uma conta.
 *
 * `vetorEnvio` e o vetor INTEIRO da origem no momento do envio (regra 2), nao so a
 * posicao dela. Sem as outras posicoes, o destino nao teria como aprender o que a
 * origem ja sabia das demais agencias, e a regra 3 (max posicao a posicao) nao teria
 * o que maximizar.
 *
 * O par (origemAgencia, vetorEnvio) identifica esta mensagem de forma unica em todo
 * o sistema: o vetor de uma agencia nunca repete, porque cada envio incrementa a
 * posicao dela. E o que permitiria deduplicar uma reentrega — a entrega do RabbitMQ
 * e AT-LEAST-ONCE, nao exactly-once.
 */
public record CreditoRemoto(
        int idConta,
        BigDecimal valor,
        List<Integer> vetorEnvio,
        int origemAgencia) {

    /** Identidade da mensagem, para log e para deduplicacao. */
    public String identidade() {
        return "agencia-" + origemAgencia + vetorEnvio;
    }
}
