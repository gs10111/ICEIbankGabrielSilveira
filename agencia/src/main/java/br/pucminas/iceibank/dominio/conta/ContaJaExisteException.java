package br.pucminas.iceibank.dominio.conta;

public class ContaJaExisteException extends RuntimeException {
    public ContaJaExisteException(String mensagem) {
        super(mensagem);
    }
}
