package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreditarRemotoRequest(
        @NotNull @Positive BigDecimal valor,
        @NotNull Integer timestampLamport,
        @NotNull Integer origemAgencia) {
}
