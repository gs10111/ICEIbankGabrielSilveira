package br.pucminas.iceibank.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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

    public static String routingKeyDe(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    public static String nomeDaFila(int idAgencia) {
        return "fila-agencia-" + idAgencia;
    }

    public String urlSegura() {
        return url.replaceAll("://([^:/]+):[^@]*@", "://$1:***@");
    }
}
