package br.pucminas.iceibank.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class MensageriaConfig {
    @Bean
    public ConnectionFactory connectionFactory(MensageriaProperties propriedades)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        com.rabbitmq.client.ConnectionFactory nativa = new com.rabbitmq.client.ConnectionFactory();
        nativa.setUri(propriedades.url());
        CachingConnectionFactory conexao = new CachingConnectionFactory(nativa);
        conexao.setConnectionNameStrategy(f -> "iceibank");
        return conexao;
    }

    @Bean
    public TopicExchange exchangeDeEventos(MensageriaProperties propriedades) {
        return new TopicExchange(propriedades.exchange(), true, false);
    }

    @Bean
    public Queue filaDaAgencia(AgenciaProperties agencia) {
        return QueueBuilder.durable(MensageriaProperties.nomeDaFila(agencia.id())).build();
    }

    @Bean
    public Binding vinculoDaAgencia(Queue filaDaAgencia, TopicExchange exchangeDeEventos, AgenciaProperties agencia) {
        return BindingBuilder.bind(filaDaAgencia)
                .to(exchangeDeEventos)
                .with(MensageriaProperties.routingKeyDe(agencia.id()));
    }

    @Bean
    public MessageConverter conversorDeMensagens() {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper mapeador = new DefaultJackson2JavaTypeMapper();
        mapeador.setTrustedPackages("br.pucminas.iceibank.mensageria");
        conversor.setJavaTypeMapper(mapeador);
        return conversor;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter conversorDeMensagens) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(conversorDeMensagens);
        return template;
    }
}
