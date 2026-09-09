package br.pucminas.iceibank.servico;

public class ChaveIdempotenciaConflitanteException extends RuntimeException {
    public ChaveIdempotenciaConflitanteException(String mensagem) {
        super(mensagem);
    }
}
