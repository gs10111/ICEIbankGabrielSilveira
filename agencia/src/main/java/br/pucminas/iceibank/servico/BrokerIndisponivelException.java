package br.pucminas.iceibank.servico;

public class BrokerIndisponivelException extends RuntimeException {
    public BrokerIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
