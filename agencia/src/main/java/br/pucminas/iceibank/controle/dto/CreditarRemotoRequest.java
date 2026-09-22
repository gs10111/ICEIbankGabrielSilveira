package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * O corpo da chamada interna entre agencias.
 *
 * `timestampVetorial` e o vetor inteiro da agencia de origem — a regra 2 manda anexar
 * o vetor todo, nao so a posicao dela. Sem as outras posicoes, o destino nao teria
 * como aprender o que a origem ja sabia das demais agencias.
 */
public record CreditarRemotoRequest(
        @NotNull @Positive BigDecimal valor,
        @NotNull @NotEmpty List<Integer> timestampVetorial,
        @NotNull Integer origemAgencia) {
}
