package br.pucminas.iceibank.mensageria;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.config.MensageriaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.repositorio.RegistroDeIdempotencia;
import br.pucminas.iceibank.servico.Publicador;
import br.pucminas.iceibank.servico.TransferenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARTE C — o lado que CONSOME.
 *
 * O consumidor precisa fazer duas coisas certas, e a segunda e a que o roteiro pede
 * para observar na tarefa 7.4: aplicar o credito quando da, e nao travar a fila
 * quando nao da.
 */
class ConsumidorDeCreditosTest {

    private static final AgenciaProperties AGENCIA_1 = new AgenciaProperties(
            1, 3,
            List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
            "target/dados-de-teste");

    private ContaRepositorio repositorio;
    private RegistroEmLista registro;
    private RelogioVetorial relogio;
    private ConsumidorDeCreditos consumidor;

    @BeforeEach
    void montarAgenciaUm() {
        repositorio = new ContaRepositorio();
        registro = new RegistroEmLista();
        relogio = new RelogioVetorial(1, 3);
        TransferenciaService transferencias = new TransferenciaService(
                AGENCIA_1, new Particionador(3), repositorio, relogio, registro,
                new PublicadorMudo(), new RegistroDeIdempotencia());
        consumidor = new ConsumidorDeCreditos(transferencias, registro, relogio);
    }

    @Test
    @DisplayName("aplica o credito e sincroniza o vetor pela regra 3")
    void aplicaOCreditoESincronizaOVetor() {
        repositorio.inserir(new Conta(1, "Bruno", new BigDecimal("800.00")));
        relogio.eventoLocal();                                     // agencia 1 em [0,1,0]

        consumidor.aoReceberCredito(new CreditoRemoto(1, new BigDecimal("40.00"), List.of(4, 0, 0), 0));

        assertThat(repositorio.buscar(1).orElseThrow().saldo()).isEqualByComparingTo("840.00");
        Evento credito = registro.eventos.get(registro.eventos.size() - 1);
        assertThat(credito.tipo()).isEqualTo("TRANSFERENCIA_CREDITO_REMOTO");
        // max([0,1,0], [4,0,0]) = [4,1,0]; +1 na propria posicao -> [4,2,0]
        assertThat(credito.carimbo()).isEqualTo(new CarimboVetorial(List.of(4, 2, 0)));
    }

    @Test
    @DisplayName("conta que nao existe: registra CREDITO_REMOTO_FALHOU e NAO relanca")
    void contaInexistenteNaoTravaAFila() {
        // Cenario da tarefa 7.4: a agencia reiniciou antes de consumir, e as contas
        // vivem em memoria. A mensagem chegou; nao ha onde aplicar.
        // Relancar devolveria a mensagem para a fila, e ela voltaria para sempre —
        // a conta 7 nao vai passar a existir sozinha.
        consumidor.aoReceberCredito(new CreditoRemoto(7, new BigDecimal("25.00"), List.of(6, 0, 0), 0));

        assertThat(registro.eventos).extracting(Evento::tipo).containsExactly("CREDITO_REMOTO_FALHOU");
        assertThat(registro.eventos.get(0).detalhes())
                .containsEntry("idConta", 7)
                .containsEntry("agenciaOrigem", 0)
                .containsEntry("mensagem", "agencia-0[6, 0, 0]");
    }

    @Test
    @DisplayName("conta de outra particao: registra a falha, nao explode")
    void contaDeOutraParticaoNaoTravaAFila() {
        // conta 2 pertence a agencia 2; esta e a agencia 1. So chegaria aqui por
        // routing key errada — mas o consumidor nao pode derrubar a fila por isso.
        consumidor.aoReceberCredito(new CreditoRemoto(2, new BigDecimal("10.00"), List.of(1, 0, 0), 0));

        assertThat(registro.eventos).extracting(Evento::tipo).containsExactly("CREDITO_REMOTO_FALHOU");
    }

    @Test
    @DisplayName("receber ja e um evento: o vetor avanca mesmo quando o credito falha")
    void oVetorAvancaMesmoQuandoFalha() {
        CarimboVetorial antes = relogio.valorAtual();

        consumidor.aoReceberCredito(new CreditoRemoto(7, new BigDecimal("25.00"), List.of(6, 0, 0), 0));

        assertThat(relogio.valorAtual()).isNotEqualTo(antes);
        // aprendeu que a agencia 0 chegou em 6, mesmo sem conseguir aplicar o valor
        assertThat(relogio.valorAtual().valorDe(0)).isEqualTo(6);
    }

    // ------------------------------------------------------------------ fakes

    private static class PublicadorMudo extends Publicador {
        PublicadorMudo() {
            super(null, new MensageriaProperties("amqp://broker-de-teste", "iceibank.eventos-de-teste"));
        }

        @Override
        public void publicarCredito(int agenciaDestino, int idConta, BigDecimal valor,
                                    CarimboVetorial vetorEnvio, int agenciaOrigem) {
            throw new UnsupportedOperationException("o consumidor nao publica nada");
        }
    }

    private static class RegistroEmLista extends RegistroDeEventos {
        final List<Evento> eventos = new ArrayList<>();

        RegistroEmLista() {
            super(AGENCIA_1);
        }

        @Override
        public synchronized Evento registrar(String tipo, CarimboVetorial carimbo, Map<String, Object> detalhes) {
            Evento evento = new Evento(AGENCIA_1.nome(), tipo, carimbo, java.time.Instant.now(), detalhes);
            eventos.add(evento);
            return evento;
        }
    }
}
