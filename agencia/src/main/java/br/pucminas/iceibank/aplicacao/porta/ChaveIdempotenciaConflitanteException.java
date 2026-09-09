package br.pucminas.iceibank.aplicacao.porta;

public class ChaveIdempotenciaConflitanteException extends RuntimeException {
    public ChaveIdempotenciaConflitanteException(String mensagem) {
        super(mensagem);
    }
}
