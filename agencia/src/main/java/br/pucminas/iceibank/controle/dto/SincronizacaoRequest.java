package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** Uma operacao que o app fez offline, com o vetor que ele tinha quando leu a conta. */
public record SincronizacaoRequest(
        @NotBlank String tipo,
        @NotNull @Positive BigDecimal valor,
        @NotEmpty List<Integer> vetorConhecido) {
}
