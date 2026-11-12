package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotNull(message = "idConta e obrigatorio") Integer idConta,
        @NotBlank(message = "senha e obrigatoria") String senha) {
}
