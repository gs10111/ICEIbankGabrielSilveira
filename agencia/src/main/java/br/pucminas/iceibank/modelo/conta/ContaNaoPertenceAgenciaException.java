package br.pucminas.iceibank.modelo.conta;

public class ContaNaoPertenceAgenciaException extends RuntimeException {
    public ContaNaoPertenceAgenciaException(String mensagem) {
        super(mensagem);
    }
}
