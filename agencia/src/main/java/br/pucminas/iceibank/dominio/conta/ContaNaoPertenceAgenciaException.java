package br.pucminas.iceibank.dominio.conta;

public class ContaNaoPertenceAgenciaException extends RuntimeException {
    public ContaNaoPertenceAgenciaException(String mensagem) {
        super(mensagem);
    }
}
