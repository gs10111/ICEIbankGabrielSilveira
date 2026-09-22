package br.pucminas.iceibank.servico;

/**
 * O broker nao aceitou a mensagem.
 *
 * E o que sobrou da falha conhecida do Sprint 1, deslocada: com mensageria, a agencia
 * de destino fora do ar ja NAO e problema (a mensagem espera na fila). Mas se o proprio
 * BROKER estiver inalcancavel, o debito local ja aconteceu e o credito nao foi nem
 * publicado. A inconsistencia mudou de lugar e ficou muito mais estreita — nao
 * desapareceu. E o que o Sprint 4 fecha com transacao distribuida.
 */
public class BrokerIndisponivelException extends RuntimeException {
    public BrokerIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
