package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.MensageriaProperties;
import br.pucminas.iceibank.mensageria.CreditoRemoto;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class Publicador {
    private final RabbitTemplate rabbit;
    private final String exchange;

    public Publicador(RabbitTemplate rabbit, MensageriaProperties propriedades) {
        this.rabbit = rabbit;
        this.exchange = propriedades.exchange();
    }

    public void publicarCredito(int agenciaDestino, int idConta, BigDecimal valor,
                                CarimboVetorial vetorEnvio, int agenciaOrigem) {
        CreditoRemoto mensagem = new CreditoRemoto(idConta, valor, vetorEnvio.valores(), agenciaOrigem);
        try {
            rabbit.convertAndSend(exchange, MensageriaProperties.routingKeyDe(agenciaDestino), mensagem,
                    envelope -> {
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
