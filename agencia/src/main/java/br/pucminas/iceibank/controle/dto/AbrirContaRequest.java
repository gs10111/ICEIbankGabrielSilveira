package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AbrirContaRequest(
        @NotNull(message = "id e obrigatorio") Integer id,
        @NotBlank(message = "nomeAluno e obrigatorio") String nomeAluno,
        @NotNull @PositiveOrZero(message = "saldoInicial nao pode ser negativo") BigDecimal saldoInicial,
        String senha) {
}
