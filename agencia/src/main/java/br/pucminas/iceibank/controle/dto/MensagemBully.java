package br.pucminas.iceibank.controle.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Corpos das duas mensagens do Bully. Mesmos nomes de campo do roteiro (origemId,
 * liderId, vetorEnvio), para a conversa ser a mesma em qualquer linguagem.
 */
public final class MensagemBully {

    private MensagemBully() {
    }

    public record Eleicao(@NotNull Integer origemId, @NotEmpty List<Integer> vetorEnvio) { }

    public record Coordenador(@NotNull Integer liderId, @NotEmpty List<Integer> vetorEnvio) { }

    /** liderAtual null = esta agencia ainda nao sabe quem e a lider (eleicao em curso). */
    public record Lider(int idAgencia, Integer liderAtual) { }
}
