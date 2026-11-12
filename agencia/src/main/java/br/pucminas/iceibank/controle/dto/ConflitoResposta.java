package br.pucminas.iceibank.controle.dto;

import java.util.List;

/** Corpo do 409 da sincronizacao: o que o app sabia, o que a agencia tem e a conta de verdade. */
public record ConflitoResposta(String erro, List<Integer> vetorConhecido, List<Integer> vetorAtual,
                               ContaResposta contaAtual) {
}
