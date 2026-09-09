package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferenciaRequest(
        String chaveIdempotencia,
        @NotNull(message = "idOrigem e obrigatorio") Integer idOrigem,
        @NotNull(message = "idDestino e obrigatorio") Integer idDestino,
        @NotNull(message = "valor e obrigatorio") @Positive(message = "valor deve ser positivo") BigDecimal valor) {
}
