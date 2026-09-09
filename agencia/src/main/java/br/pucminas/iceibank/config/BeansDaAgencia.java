package br.pucminas.iceibank.config;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.servico.ExtratoConsolidadoService;
import br.pucminas.iceibank.servico.ContaRepositorio;
import br.pucminas.iceibank.servico.RegistroIdempotencia;
import br.pucminas.iceibank.servico.TransferenciaIdempotente;
import br.pucminas.iceibank.servico.TransferenciaService;
import br.pucminas.iceibank.servico.Transferir;
import br.pucminas.iceibank.repositorio.RegistroIdempotenciaEmMemoria;
import br.pucminas.iceibank.servico.AgenciaRemotaRest;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
import br.pucminas.iceibank.modelo.relogio.RelogioLogico;
import br.pucminas.iceibank.repositorio.RegistroEventosJsonl;
import br.pucminas.iceibank.repositorio.ContaRepositorioEmMemoria;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Unico ponto onde as portas sao ligadas aos adapters.
 *
 * Nem dominio nem aplicacao tem anotacao de Spring: quem monta o grafo de
 * objetos e esta classe, na infra. Trocar um adapter (Sprint 2: REST -> fila;
 * Sprint 4: memoria -> banco) e editar UMA linha aqui.
 */
@Configuration
public class BeansDaAgencia {

    @Bean
    public Particionador particionador(AgenciaProperties propriedades) {
        return new Particionador(propriedades.total());
    }

    /** Um relogio logico por processo — e o relogio DESTA agencia. */
    @Bean
    public RelogioLogico relogioLogico() {
        return new RelogioLamport();
    }

    /**
     * Um unico bean serve as duas portas de evento: RegistroEventos (escrita) e
     * ConsultaEventos (leitura). O Spring resolve as interfaces a partir do tipo
     * concreto — declarar beans-ponte adicionais so criaria ambiguidade.
     */
    @Bean
    public RegistroEventosJsonl registroEventos(AgenciaProperties propriedades) {
        return new RegistroEventosJsonl(propriedades.nome(), Path.of(propriedades.pastaDados()));
    }

    @Bean
    public ContaRepositorio contaRepositorio() {
        return new ContaRepositorioEmMemoria();
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

    /** Um so adapter serve as duas portas remotas (escrita e leitura). */
    @Bean
    public AgenciaRemotaRest agenciaRemotaRest(AgenciaProperties propriedades,
                                              RestClient restClientEntreAgencias,
                                              br.pucminas.iceibank.seguranca.SegurancaProperties seguranca) {
        return new AgenciaRemotaRest(propriedades, restClientEntreAgencias, seguranca.tokenEntreAgencias());
    }

    @Bean
    public ExtratoConsolidadoService extratoConsolidadoService(AgenciaProperties propriedades,
                                                              Particionador particionador,
                                                              ContaRepositorio repositorio,
                                                              AgenciaRemotaRest adapterRemoto) {
        // O parametro do construtor e a INTERFACE ConsultaContaRemota: o servico
        // continua sem conhecer REST. Aqui na infra podemos falar do tipo concreto.
        return new ExtratoConsolidadoService(propriedades.id(), particionador, repositorio, adapterRemoto);
    }

    @Bean
    public RegistroIdempotencia registroIdempotencia() {
        return new RegistroIdempotenciaEmMemoria();
    }

    @Bean
    public TransferenciaService transferenciaService(AgenciaProperties propriedades,
                                                     Particionador particionador,
                                                     ContaRepositorio repositorio,
                                                     RelogioLogico relogio,
                                                     RegistroEventosJsonl registro,
                                                     AgenciaRemotaRest adapterRemoto) {
        return new TransferenciaService(propriedades.id(), particionador, repositorio,
                relogio, registro, adapterRemoto);
    }

    /**
     * A COMPOSICAO da idempotencia aparece aqui, numa linha: o servico "puro" e
     * envolvido pelo decorator. TransferenciaService nunca soube que ela existe.
     */
    @Bean
    public Transferir transferir(TransferenciaService servico, RegistroIdempotencia registroIdempotencia) {
        return new TransferenciaIdempotente(servico, registroIdempotencia);
    }

    @Bean
    public ContaService contaService(AgenciaProperties propriedades,
                                     Particionador particionador,
                                     ContaRepositorio repositorio,
                                     RelogioLogico relogio,
                                     RegistroEventosJsonl registro) {
        return new ContaService(propriedades.id(), particionador, repositorio, relogio, registro, registro);
    }
}
