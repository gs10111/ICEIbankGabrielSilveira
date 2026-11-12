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
import br.pucminas.iceibank.repositorio.RegistroDeMensagensProcessadas;
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
        consumidor = new ConsumidorDeCreditos(transferencias, registro, relogio,
                new RegistroDeMensagensProcessadas());
    }

    @Test
    @DisplayName("aplica o credito e sincroniza o vetor pela regra 3")
    void aplicaOCreditoESincronizaOVetor() {
        repositorio.inserir(new Conta(1, "Bruno", new BigDecimal("800.00")));
        relogio.eventoLocal();

        consumidor.aoReceberCredito(new CreditoRemoto(1, new BigDecimal("40.00"), List.of(4, 0, 0), 0));

        assertThat(repositorio.buscar(1).orElseThrow().saldo()).isEqualByComparingTo("840.00");
        Evento credito = registro.eventos.get(registro.eventos.size() - 1);
        assertThat(credito.tipo()).isEqualTo("TRANSFERENCIA_CREDITO_REMOTO");
        assertThat(credito.carimbo()).isEqualTo(new CarimboVetorial(List.of(4, 2, 0)));
    }

    @Test
    @DisplayName("conta que nao existe: registra CREDITO_REMOTO_FALHOU e NAO relanca")
    void contaInexistenteNaoTravaAFila() {
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
        consumidor.aoReceberCredito(new CreditoRemoto(2, new BigDecimal("10.00"), List.of(1, 0, 0), 0));

        assertThat(registro.eventos).extracting(Evento::tipo).containsExactly("CREDITO_REMOTO_FALHOU");
    }

    @Test
    @DisplayName("reentrega da MESMA mensagem credita uma vez so")
    void reentregaNaoCreditaDuasVezes() {
        repositorio.inserir(new Conta(1, "Bruno", new BigDecimal("800.00")));
        CreditoRemoto mensagem = new CreditoRemoto(1, new BigDecimal("40.00"), List.of(4, 0, 0), 0);

        consumidor.aoReceberCredito(mensagem);
        consumidor.aoReceberCredito(mensagem);

        assertThat(repositorio.buscar(1).orElseThrow().saldo()).isEqualByComparingTo("840.00");
        assertThat(registro.eventos).extracting(Evento::tipo)
                .containsExactly("TRANSFERENCIA_CREDITO_REMOTO", "CREDITO_REMOTO_DUPLICADO");
    }

    @Test
    @DisplayName("mensagens diferentes da mesma origem continuam passando as duas")
    void mensagensDistintasNaoSaoConfundidas() {
        repositorio.inserir(new Conta(1, "Bruno", new BigDecimal("800.00")));

        consumidor.aoReceberCredito(new CreditoRemoto(1, new BigDecimal("40.00"), List.of(4, 0, 0), 0));
        consumidor.aoReceberCredito(new CreditoRemoto(1, new BigDecimal("10.00"), List.of(5, 0, 0), 0));

        assertThat(repositorio.buscar(1).orElseThrow().saldo()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("receber ja e um evento: o vetor avanca mesmo quando o credito falha")
    void oVetorAvancaMesmoQuandoFalha() {
        CarimboVetorial antes = relogio.valorAtual();

        consumidor.aoReceberCredito(new CreditoRemoto(7, new BigDecimal("25.00"), List.of(6, 0, 0), 0));

        assertThat(relogio.valorAtual()).isNotEqualTo(antes);
        assertThat(relogio.valorAtual().valorDe(0)).isEqualTo(6);
    }

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
