package br.pucminas.iceibank.infra.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ValorRequest(
        @NotNull(message = "valor e obrigatorio") @Positive(message = "valor deve ser positivo") BigDecimal valor) {
}
