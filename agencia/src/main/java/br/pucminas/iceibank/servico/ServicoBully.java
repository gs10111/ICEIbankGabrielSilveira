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
            registro.registrar("COORDENADOR_RECUSADO", carimbo, Map.of("de", liderId, "minhaId", idAgencia));
            paraEleicoes.execute(this::iniciarEleicao);
            return;
        }
        liderAtual.set(liderId);
        registro.registrar("LIDER_ANUNCIADO", carimbo, Map.of("liderId", liderId, "agencia", idAgencia));
    }

    public void verificarLider() {
        Integer lider = liderAtual.get();
        if (lider == null) {
            iniciarEleicao();
            return;
        }
        if (lider == idAgencia) {
            return;
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

    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir(ApplicationReadyEvent evento) {
        BullyProperties bully = evento.getApplicationContext().getBean(BullyProperties.class);
        if (bully.ativo() && agenda != null) {
            agenda.execute(this::iniciarEleicao);
            agenda.scheduleWithFixedDelay(this::verificarComSeguranca,
                    bully.intervaloMs(), bully.intervaloMs(), TimeUnit.MILLISECONDS);
        }
    }

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
