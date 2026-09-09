package br.pucminas.iceibank.config;

import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * O que o Spring nao consegue montar sozinho.
 *
 * Servicos, repositorios e controllers sao descobertos por anotacao. Sobram tres
 * objetos: dois do MODELO — que de proposito nao carregam anotacao de framework,
 * para continuarem testaveis sem Spring — e o cliente HTTP, que precisa de
 * timeouts explicitos.
 */
@Configuration
public class BeansDaAgencia {

    @Bean
    public Particionador particionador(AgenciaProperties propriedades) {
        return new Particionador(propriedades.total());
    }

    /** Um relogio logico por processo — e o relogio DESTA agencia. */
    @Bean
    public RelogioLamport relogioLamport() {
        return new RelogioLamport();
    }

    /**
     * Timeouts curtos de proposito: quando a agencia de destino esta fora do ar,
     * a falha da Parte D precisa aparecer em segundos, nao pendurar a requisicao.
     */
    @Bean
    public RestClient restClientEntreAgencias() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
