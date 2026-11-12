package br.pucminas.iceibank.config;

import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class BeansDaAgencia {
    @Bean
    public Particionador particionador(AgenciaProperties propriedades) {
        return new Particionador(propriedades.total());
    }

    @Bean
    public RelogioVetorial relogioVetorial(AgenciaProperties propriedades, RegistroDeEventos registroDeEventos) {
        return new RelogioVetorial(propriedades.id(), registroDeEventos.vetorRestaurado(propriedades.total()));
    }

    @Bean
    public RestClient restClientEntreAgencias() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
