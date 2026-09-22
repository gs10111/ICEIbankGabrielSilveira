package br.pucminas.iceibank.config;

import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
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

    /**
     * Um relogio VETORIAL por processo — e o relogio DESTA agencia.
     *
     * Precisa saber duas coisas que o de Lamport nao precisava: qual posicao do vetor
     * e a sua (AGENCIA_ID) e quantas posicoes o vetor tem (AGENCIA_TOTAL).
     *
     * Restaurado do log ao subir: o .jsonl e append-only e sobrevive ao restart, entao
     * um vetor zerado produziria carimbos menores que os ja gravados — o defeito que o
     * commit e1bb956 corrigiu no Sprint 1 e que aqui ja nasce corrigido.
     */
    @Bean
    public RelogioVetorial relogioVetorial(AgenciaProperties propriedades, RegistroDeEventos registroDeEventos) {
        return new RelogioVetorial(propriedades.id(), registroDeEventos.vetorRestaurado(propriedades.total()));
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
