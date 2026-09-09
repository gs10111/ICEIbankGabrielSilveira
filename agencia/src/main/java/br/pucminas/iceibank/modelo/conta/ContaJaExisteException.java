package br.pucminas.iceibank.modelo.conta;

public class ContaJaExisteException extends RuntimeException {
    public ContaJaExisteException(String mensagem) {
        super(mensagem);
    }
}
