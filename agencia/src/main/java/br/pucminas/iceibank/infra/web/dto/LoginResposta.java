package br.pucminas.iceibank.infra.web.dto;

public record LoginResposta(String token, long expiraEmSegundos, int idConta, String nomeAluno, int agencia) {
}
