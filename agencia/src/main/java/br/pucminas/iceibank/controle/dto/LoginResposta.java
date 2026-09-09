package br.pucminas.iceibank.controle.dto;

public record LoginResposta(String token, long expiraEmSegundos, int idConta, String nomeAluno, int agencia) {
}
