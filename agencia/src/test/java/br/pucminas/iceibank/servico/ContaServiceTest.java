package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContaServiceTest {

    private static final int ID_AGENCIA = 0;

    private static final AgenciaProperties PROPRIEDADES = new AgenciaProperties(
            ID_AGENCIA, 3,
            List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
            "target/dados-de-teste");

    private ContaRepositorio repositorio;
    private RegistroEmLista registro;
    private ContaService servico;

    @BeforeEach
    void montarServicoDaAgenciaZero() {
        repositorio = new ContaRepositorio();
        registro = new RegistroEmLista();
        servico = new ContaService(
                PROPRIEDADES, new Particionador(3), repositorio, new RelogioVetorial(ID_AGENCIA, 3), registro);
    }

    // ---------- abertura ----------

    @Test
    @DisplayName("abrir cria a conta e ela pode ser consultada depois")
    void abrirCriaAContaNoRepositorio() {
        Conta criada = servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(criada.id()).isEqualTo(0);
        assertThat(criada.nome()).isEqualTo("Ana");
        assertThat(criada.saldo()).isEqualByComparingTo("100.00");
        assertThat(repositorio.buscar(0)).isPresent();
    }

    @Test
    @DisplayName("agencia recusa abrir conta que nao e sua (1 % 3 == 1, nao 0)")
    void recusaContaDeOutraAgencia() {
        assertThrows(ContaNaoPertenceAgenciaException.class,
                () -> servico.abrir(1, "Bia", new BigDecimal("50.00")));

        assertThat(repositorio.existe(1)).isFalse();
    }

    @Test
    @DisplayName("abrir conta que ja existe e rejeitado")
    void recusaContaDuplicada() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThrows(ContaJaExisteException.class,
                () -> servico.abrir(0, "Outra", new BigDecimal("999.00")));
    }

    @Test
    @DisplayName("validacao que falha nao consome carimbo nem gera evento")
    void validacaoQueFalhaNaoCarimba() {
        assertThrows(ContaNaoPertenceAgenciaException.class,
                () -> servico.abrir(1, "Bia", new BigDecimal("50.00")));

        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(registro.eventos).hasSize(1);
        assertThat(registro.eventos.get(0).carimbo()).isEqualTo(vetor(1, 0, 0));
    }

    // ---------- consulta ----------

    @Test
    @DisplayName("consultar devolve a conta cadastrada")
    void consultaDevolveAConta() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(servico.consultar(0).nome()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("consultar conta inexistente nesta agencia e rejeitado")
    void consultaInexistenteEhRejeitada() {
        assertThrows(ContaNaoEncontradaException.class, () -> servico.consultar(3));
    }

    // ---------- deposito e saque ----------

    @Test
    @DisplayName("deposito aumenta o saldo da conta")
    void depositoAumentaSaldo() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(servico.depositar(0, new BigDecimal("25.00")).saldo()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("saque diminui o saldo da conta")
    void saqueDiminuiSaldo() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(servico.sacar(0, new BigDecimal("30.00")).saldo()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("depositar em conta inexistente e rejeitado")
    void depositoEmContaInexistenteEhRejeitado() {
        assertThrows(ContaNaoEncontradaException.class,
                () -> servico.depositar(0, new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("saque acima do saldo e rejeitado pela propria Conta")
    void saqueAcimaDoSaldoEhRejeitado() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThrows(SaldoInsuficienteException.class,
                () -> servico.sacar(0, new BigDecimal("150.00")));
    }

    // ---------- relogio de Lamport aplicado ----------

    @Test
    @DisplayName("abrir conta registra evento CRIAR_CONTA carimbado com 1")
    void abrirRegistraEventoCarimbado() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(registro.eventos).hasSize(1);
        Evento evento = registro.eventos.get(0);
        assertThat(evento.tipo()).isEqualTo("CRIAR_CONTA");
        assertThat(evento.carimbo()).isEqualTo(vetor(1, 0, 0));
    }

    @Test
    @DisplayName("operacoes sucessivas avancam SO a propria posicao: [1,0,0], [2,0,0], [3,0,0]")
    void operacoesSucessivasAvancamORelogio() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));
        servico.depositar(0, new BigDecimal("10.00"));
        servico.sacar(0, new BigDecimal("5.00"));

        assertThat(registro.eventos).extracting(Evento::carimbo)
                .containsExactly(vetor(1, 0, 0), vetor(2, 0, 0), vetor(3, 0, 0));
        assertThat(registro.eventos).extracting(Evento::tipo)
                .containsExactly("CRIAR_CONTA", "DEPOSITO", "SAQUE");
    }

    @Test
    @DisplayName("consulta nao gera evento: leitura nao avanca o relogio")
    void consultaNaoGeraEvento() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        servico.consultar(0);
        servico.consultar(0);

        assertThat(registro.eventos).hasSize(1);
    }

    // ---------- funcionalidade adicional 1: historico ----------

    @Test
    @DisplayName("historico devolve os eventos da conta, do mais recente para o mais antigo")
    void historicoDevolveEventosDaConta() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));
        servico.depositar(0, new BigDecimal("10.00"));
        servico.sacar(0, new BigDecimal("5.00"));

        List<Evento> historico = servico.historico(0, 10);

        assertThat(historico).extracting(Evento::tipo)
                .containsExactly("SAQUE", "DEPOSITO", "CRIAR_CONTA");
    }

    @Test
    @DisplayName("historico respeita o limite pedido")
    void historicoRespeitaOLimite() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));
        servico.depositar(0, new BigDecimal("10.00"));
        servico.sacar(0, new BigDecimal("5.00"));

        assertThat(servico.historico(0, 2)).hasSize(2);
    }

    @Test
    @DisplayName("historico de conta inexistente e rejeitado")
    void historicoDeContaInexistenteEhRejeitado() {
        assertThrows(ContaNaoEncontradaException.class, () -> servico.historico(0, 10));
    }

    /** Registro de eventos que fica na memoria, para o teste nao tocar disco. */
    private static class RegistroEmLista extends RegistroDeEventos {
        final List<Evento> eventos = new ArrayList<>();

        RegistroEmLista() {
            super(PROPRIEDADES);
        }

        @Override
        public Evento registrar(String tipo, CarimboVetorial carimbo, Map<String, Object> detalhes) {
            Evento evento = new Evento("teste", tipo, carimbo, Instant.now(), detalhes);
            eventos.add(evento);
            return evento;
        }

        @Override
        public List<Evento> ultimosDaConta(int idConta, int limite) {
            List<Evento> daConta = new ArrayList<>(eventos.stream()
                    .filter(e -> e.detalhes().get("id") instanceof Number n && n.intValue() == idConta)
                    .toList());
            Collections.reverse(daConta);
            return daConta.size() > limite ? daConta.subList(0, limite) : daConta;
        }

        @Override
        public List<Evento> ultimos(int limite) {
            List<Evento> todos = new ArrayList<>(eventos);
            Collections.reverse(todos);
            return todos.size() > limite ? todos.subList(0, limite) : todos;
        }

        @Override
        public int quantidade() {
            return eventos.size();
        }
    }

    /** Atalho: vetor(1, 0, 0) em vez de new CarimboVetorial(List.of(1, 0, 0)). */
    private static CarimboVetorial vetor(int... valores) {
        return new CarimboVetorial(java.util.Arrays.stream(valores).boxed().toList());
    }
}
