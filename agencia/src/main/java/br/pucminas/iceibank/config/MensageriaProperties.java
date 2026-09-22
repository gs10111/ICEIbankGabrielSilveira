package br.pucminas.iceibank.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * PARTE A — configuracao do broker.
 *
 * `url` e SEGREDO (usuario, senha e vhost do CloudAMQP) e por isso NAO tem default
 * no application.yml: vem de RABBITMQ_URL. Se faltar, a aplicacao nao sobe — melhor
 * falhar no boot com mensagem clara do que estourar um erro de conexao confuso na
 * primeira transferencia.
 */
@Validated
@ConfigurationProperties(prefix = "mensageria")
public record MensageriaProperties(
        @NotBlank(message = "defina a variavel de ambiente RABBITMQ_URL com a URL AMQP do broker") String url,
        String exchange) {

    public MensageriaProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "iceibank.eventos";
        }
    }

    /** A routing key que entrega na fila de UMA agencia especifica. */
    public static String routingKeyDe(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    public static String nomeDaFila(int idAgencia) {
        return "fila-agencia-" + idAgencia;
    }

    /** A URL sem a senha, para aparecer em log sem vazar credencial. */
    public String urlSegura() {
        return url.replaceAll("://([^:/]+):[^@]*@", "://$1:***@");
    }
}
