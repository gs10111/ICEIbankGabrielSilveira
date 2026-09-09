package br.pucminas.iceibank.aplicacao.porta;

public class AgenciaRemotaIndisponivelException extends RuntimeException {
    public AgenciaRemotaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
