package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DECISAO DE DESIGN (Parte F): as credenciais sao <numero da conta, senha>.
 *
 * Motivo: a conta ja e a identidade do sistema, ja e particionada por `id % 3` e
 * ja aparece em toda operacao. Um "usuario" separado exigiria um segundo modelo de
 * identidade e uma tabela de vinculo, sem acrescentar nada ao que o sprint estuda.
 */
public record LoginRequest(
        @NotNull(message = "idConta e obrigatorio") Integer idConta,
        @NotBlank(message = "senha e obrigatoria") String senha) {
}
