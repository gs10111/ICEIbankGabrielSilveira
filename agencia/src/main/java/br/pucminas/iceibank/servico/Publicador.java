package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.MensageriaProperties;
import br.pucminas.iceibank.mensageria.CreditoRemoto;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * PARTE C — a saida de mensagens desta agencia.
 *
 * Substitui a chamada REST direta do Sprint 1. Quem publica nao sabe (nem precisa
 * saber) quem vai consumir: entrega para a EXCHANGE com uma routing key, e o broker
 * decide em qual fila a mensagem cai. E essa indirecao que da nome a unidade.
 *
 * A diferenca pratica: no Sprint 1, destino fora do ar = a chamada falhava na hora e
 * o debito ficava pendurado. Aqui a mensagem e PERSISTENTE numa fila DURAVEL — o
 * broker a guarda em disco ate a agencia de destino voltar e consumir.
 */
@Service
public class Publicador {

    private final RabbitTemplate rabbit;
    private final String exchange;

    public Publicador(RabbitTemplate rabbit, MensageriaProperties propriedades) {
        this.rabbit = rabbit;
        this.exchange = propriedades.exchange();
    }

    /**
     * Publica o credito para a agencia dona da conta de destino.
     *
     * Nao ha retorno nem confirmacao sincrona: o sucesso aqui significa "o broker
     * aceitou a mensagem", nao "o dinheiro chegou". Essa distincao e o coracao do
     * sprint — ver a pergunta 7.5.2 do roteiro.
     */
    public void publicarCredito(int agenciaDestino, int idConta, BigDecimal valor,
                                CarimboVetorial vetorEnvio, int agenciaOrigem) {
        CreditoRemoto mensagem = new CreditoRemoto(idConta, valor, vetorEnvio.valores(), agenciaOrigem);
        try {
            rabbit.convertAndSend(exchange, MensageriaProperties.routingKeyDe(agenciaDestino), mensagem,
                    envelope -> {
                        // Persistente: sobrevive a um restart do proprio broker.
                        envelope.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        envelope.getMessageProperties().setMessageId(mensagem.identidade());
                        return envelope;
                    });
        } catch (AmqpException e) {
            throw new BrokerIndisponivelException(
                    "falha ao publicar credito para a agencia " + agenciaDestino + ": " + e.getMessage(), e);
        }
    }
}
