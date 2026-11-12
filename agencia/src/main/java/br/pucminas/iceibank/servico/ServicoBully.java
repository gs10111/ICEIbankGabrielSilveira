package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.config.BullyProperties;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SPRINT 3 — eleicao de lider pelo algoritmo de Bully (Garcia-Molina, 1982).
 *
 * Vence sempre a agencia de MAIOR id entre as que estao no ar:
 *   1. quem inicia uma eleicao manda ELEICAO para todas as de id maior;
 *   2. se nenhuma responde, ela vence e manda COORDENADOR para todas;
 *   3. se alguma responde, essa assume a eleicao — quem perguntou so espera o anuncio;
 *   4. quem recebe ELEICAO de id menor responde (o 200 da rota) e inicia a propria.
 *
 * CONCORRENCIA. No exemplo em Node do roteiro tudo roda numa thread so. Aqui nao:
 * as mensagens chegam em threads do Tomcat e a eleicao roda na thread da agenda. Por
 * isso `liderAtual` e `eleicaoEmAndamento` sao atomicos (secao 11 do roteiro).
 *
 * POR QUE A ELEICAO RECEBIDA RODA EM OUTRA THREAD. Quem recebe ELEICAO precisa
 * responder 200 JA — esse 200 e o "estou viva" que faz a remetente desistir. Se a
 * propria eleicao rodasse dentro da requisicao, a resposta so sairia depois de
 * perguntar as de id maior, e a remetente estouraria o timeout achando que esta
 * agencia caiu.
 */
@Service
public class ServicoBully {

    private final int idAgencia;
    private final int totalDeAgencias;
    private final RelogioVetorial relogio;
    private final RegistroDeEventos registro;
    private final MensageiroBully mensageiro;
    private final Executor paraEleicoes;
    private final ScheduledExecutorService agenda;

    private final AtomicReference<Integer> liderAtual = new AtomicReference<>(null);
    private final AtomicBoolean eleicaoEmAndamento = new AtomicBoolean(false);

    @Autowired
    public ServicoBully(AgenciaProperties agencia, RelogioVetorial relogio, RegistroDeEventos registro,
                        MensageiroBully mensageiro) {
        this(agencia, relogio, registro, mensageiro, Executors.newSingleThreadScheduledExecutor(ServicoBully::daemon));
    }

    /** Para os testes: a eleicao "assincrona" pode rodar na propria thread, e o teste fica deterministico. */
    ServicoBully(AgenciaProperties agencia, RelogioVetorial relogio, RegistroDeEventos registro,
                 MensageiroBully mensageiro, Executor paraEleicoes) {
        this.idAgencia = agencia.id();
        this.totalDeAgencias = agencia.total();
        this.relogio = relogio;
        this.registro = registro;
        this.mensageiro = mensageiro;
        this.paraEleicoes = paraEleicoes;
        this.agenda = paraEleicoes instanceof ScheduledExecutorService s ? s : null;
    }

    public void iniciarEleicao() {
        // compareAndSet e o `if (eleicaoEmAndamento) return; eleicaoEmAndamento = true`
        // do roteiro feito de uma vez so: duas threads nao passam juntas por aqui.
        if (!eleicaoEmAndamento.compareAndSet(false, true)) {
            return;
        }
        try {
            registro.registrar("ELEICAO_INICIADA", relogio.eventoLocal(), Map.of("idAgencia", idAgencia));

            boolean alguemMaiorRespondeu = false;
            for (int destino = idAgencia + 1; destino < totalDeAgencias; destino++) {
                if (mensageiro.enviarEleicao(destino, idAgencia, relogio.aoEnviar())) {
                    alguemMaiorRespondeu = true;
                }
            }

            if (!alguemMaiorRespondeu) {
                tornarMeLider();
            }
            // senao: a de id maior conduz a propria eleicao e anuncia via /bully/coordenador
        } finally {
            eleicaoEmAndamento.set(false);
        }
    }

    public void receberEleicao(int origemId, CarimboVetorial vetorRecebido) {
        exigirAgenciaDaMalha(origemId);
        registro.registrar("ELEICAO_RECEBIDA", relogio.aoReceber(vetorRecebido),
                Map.of("de", origemId, "minhaId", idAgencia));
        paraEleicoes.execute(this::iniciarEleicao);
    }

    public void receberCoordenador(int liderId, CarimboVetorial vetorRecebido) {
        exigirAgenciaDaMalha(liderId);
        CarimboVetorial carimbo = relogio.aoReceber(vetorRecebido);
        if (liderId < idAgencia) {
            // Eu estou viva e tenho id maior: esse anuncio esta desatualizado.
            registro.registrar("COORDENADOR_RECUSADO", carimbo, Map.of("de", liderId, "minhaId", idAgencia));
            paraEleicoes.execute(this::iniciarEleicao);
            return;
        }
        liderAtual.set(liderId);
        registro.registrar("LIDER_ANUNCIADO", carimbo, Map.of("liderId", liderId, "agencia", idAgencia));
    }

    /**
     * SPRINT 3, Parte B — o heartbeat. Roda a cada `bully.intervalo-ms`.
     *
     * O compareAndSet ao esquecer a lider e de proposito: entre o ping que falhou e
     * esta linha pode ter chegado um anuncio de coordenador NOVO, numa thread do
     * Tomcat. Um `set(null)` cego apagaria essa lider recem-anunciada.
     */
    public void verificarLider() {
        Integer lider = liderAtual.get();
        if (lider == null) {
            iniciarEleicao();
            return;
        }
        if (lider == idAgencia) {
            return;                                   // eu sou a lider, nada a verificar
        }
        if (mensageiro.estaViva(lider)) {
            return;
        }
        registro.registrar("LIDER_NAO_RESPONDEU", relogio.eventoLocal(),
                Map.of("liderAtual", lider, "agencia", idAgencia));
        liderAtual.compareAndSet(lider, null);
        iniciarEleicao();
    }

    public Optional<Integer> liderAtual() {
        return Optional.ofNullable(liderAtual.get());
    }

    public int idAgencia() {
        return idAgencia;
    }

    /** O equivalente ao callback do app.listen() do roteiro: so com o servidor ja no ar. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir(ApplicationReadyEvent evento) {
        BullyProperties bully = evento.getApplicationContext().getBean(BullyProperties.class);
        if (bully.ativo() && agenda != null) {
            agenda.execute(this::iniciarEleicao);
            // fixedDelay e nao fixedRate: se um ping demorar o timeout inteiro, a proxima
            // verificacao espera o intervalo a partir do FIM desta, sem empilhar.
            agenda.scheduleWithFixedDelay(this::verificarComSeguranca,
                    bully.intervaloMs(), bully.intervaloMs(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Uma excecao que escape de uma tarefa agendada CANCELA o agendamento em silencio
     * (contrato do ScheduledExecutorService). O heartbeat morreria na primeira falha
     * de disco ao registrar evento, e o failover pararia sem nenhum aviso.
     */
    private void verificarComSeguranca() {
        try {
            verificarLider();
        } catch (RuntimeException e) {
            System.err.println("[bully] heartbeat falhou, tenta de novo no proximo ciclo: " + e);
        }
    }

    @PreDestroy
    public void encerrar() {
        if (agenda != null) {
            agenda.shutdownNow();
        }
    }

    private void tornarMeLider() {
        liderAtual.set(idAgencia);
        registro.registrar("TORNOU_LIDER", relogio.eventoLocal(), Map.of("idAgencia", idAgencia));
        for (int destino = 0; destino < totalDeAgencias; destino++) {
            if (destino != idAgencia) {
                mensageiro.anunciarCoordenador(destino, idAgencia, relogio.aoEnviar());
            }
        }
    }

    /** Sem isto, um anuncio de "lider 7" seria aceito e o heartbeat estouraria ao procurar a URL dela. */
    private void exigirAgenciaDaMalha(int id) {
        if (id < 0 || id >= totalDeAgencias) {
            throw new IllegalArgumentException("agencia " + id + " fora de uma malha de " + totalDeAgencias);
        }
    }

    private static Thread daemon(Runnable tarefa) {
        Thread thread = new Thread(tarefa, "bully");
        thread.setDaemon(true);
        return thread;
    }
}
