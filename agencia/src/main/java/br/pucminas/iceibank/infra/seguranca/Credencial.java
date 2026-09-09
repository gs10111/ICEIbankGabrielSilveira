package br.pucminas.iceibank.infra.seguranca;

/**
 * Credencial fica FORA do dominio: a Conta cuida de saldo, nao de autenticacao.
 * Se a senha morasse na Conta, mudar a politica de senha exigiria mexer na
 * classe que guarda dinheiro (violacao do S).
 */
public record Credencial(int idConta, String senhaCifrada) {
}
