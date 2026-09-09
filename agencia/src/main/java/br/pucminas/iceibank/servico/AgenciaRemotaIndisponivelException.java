package br.pucminas.iceibank.servico;

public class AgenciaRemotaIndisponivelException extends RuntimeException {
    public AgenciaRemotaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
