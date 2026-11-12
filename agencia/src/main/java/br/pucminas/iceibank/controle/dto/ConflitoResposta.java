package br.pucminas.iceibank.controle.dto;

import java.util.List;

public record ConflitoResposta(String erro, List<Integer> vetorConhecido, List<Integer> vetorAtual,
                               ContaResposta contaAtual) {
}
