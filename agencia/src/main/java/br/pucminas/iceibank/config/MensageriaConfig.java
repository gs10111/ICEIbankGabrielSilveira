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

/**
 * PARTE A — a topologia do RabbitMQ, declarada em codigo.
 *
 * Uma exchange TOPIC compartilhada pelas 3 agencias (`iceibank.eventos`), e UMA fila
 * por agencia (`fila-agencia-<id>`) vinculada pela routing key `agencia.<id>.creditar`.
 * Quando a agencia 0 quer creditar uma conta da agencia 1, publica com a routing key
 * `agencia.1.creditar` — so a fila da agencia 1 recebe, mesmo a exchange sendo comum.
 *
 * Por que topic e nao direct: direct exige que a routing key bata exatamente, o que
 * daria no mesmo hoje. Topic deixa a porta aberta para curingas (`agencia.*.creditar`
 * entregaria a todas), que e exatamente o que a fila de auditoria precisa.
 *
 * `durable` na exchange e na fila, e mensagem PERSISTENTE (padrao do RabbitTemplate
 * com este template): e isso que faz uma mensagem publicada para uma agencia fora do
 * ar NAO se perder — ela fica gravada no broker ate alguem consumir. E a diferenca
 * central em relacao a chamada REST direta do Sprint 1.
 *
 * As declaracoes acontecem quando a primeira conexao e aberta (o RabbitAdmin do Spring
 * Boot cuida disso), nao no startup: sem isso, um broker fora do ar impediria a agencia
 * de subir, e o objetivo do sprint e justamente tolerar indisponibilidade.
 */
@Configuration
public class MensageriaConfig {

    /**
     * Conexao construida a partir da URL AMQP inteira.
     *
     * A URL do CloudAMQP e `amqps://usuario:senha@host/vhost` — usuario, senha, host,
     * TLS e vhost num campo so. Deixar o cliente nativo interpretar a URI evita quebrar
     * isso em cinco propriedades e errar o vhost (que e a pegadinha mais comum).
     */
    @Bean
    public ConnectionFactory connectionFactory(MensageriaProperties propriedades)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        com.rabbitmq.client.ConnectionFactory nativa = new com.rabbitmq.client.ConnectionFactory();
        nativa.setUri(propriedades.url());                  // amqps:// ja liga o TLS sozinho
        CachingConnectionFactory conexao = new CachingConnectionFactory(nativa);
        conexao.setConnectionNameStrategy(f -> "iceibank");
        return conexao;
    }

    @Bean
    public TopicExchange exchangeDeEventos(MensageriaProperties propriedades) {
        return new TopicExchange(propriedades.exchange(), true, false);
    }

    /** A fila DESTA agencia. Cada processo declara a sua — o mesmo jar, id diferente. */
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

    /**
     * Mensagens em JSON, nao em serializacao binaria do Java.
     *
     * O roteiro descreve o corpo como JSON, e um log de broker legivel vale muito na
     * hora de depurar. `trustedPackages` fecha a porta para desserializar tipo
     * arbitrario vindo da rede.
     */
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
