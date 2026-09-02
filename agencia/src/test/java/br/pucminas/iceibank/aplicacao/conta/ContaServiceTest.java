package br.pucminas.iceibank.aplicacao.conta;

import br.pucminas.iceibank.aplicacao.porta.RegistroEventos;
import br.pucminas.iceibank.dominio.conta.ContaJaExisteException;
import br.pucminas.iceibank.dominio.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.dominio.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.dominio.conta.Conta;
import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.particao.Particionador;
import br.pucminas.iceibank.dominio.relogio.Carimbo;
import br.pucminas.iceibank.dominio.relogio.CarimboLamport;
import br.pucminas.iceibank.dominio.relogio.RelogioLamport;
import br.pucminas.iceibank.infra.memoria.ContaRepositorioEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContaServiceTest {

    private static final int ID_AGENCIA = 0;

    private ContaRepositorioEmMemoria repositorio;
    private RegistroEmLista registro;
    private ContaService servico;

    @BeforeEach
    void montarServicoDaAgenciaZero() {
        repositorio = new ContaRepositorioEmMemoria();
        registro = new RegistroEmLista();
        servico = new ContaService(
                ID_AGENCIA,
                new Particionador(3),
                repositorio,
                new RelogioLamport(),
                registro);
    }

    @Test
    @DisplayName("abrir cria a conta e ela pode ser consultada depois")
    void abrirCriaAContaNoRepositorio() {
        Conta criada = servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(criada.id()).isEqualTo(0);
        assertThat(criada.nome()).isEqualTo("Ana");
        assertThat(criada.saldo()).isEqualByComparingTo("100.00");
        assertThat(repositorio.buscar(0)).isPresent();
    }

    // ---------- particao ----------

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

        Conta depois = servico.depositar(0, new BigDecimal("25.00"));

        assertThat(depois.saldo()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("saque diminui o saldo da conta")
    void saqueDiminuiSaldo() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        Conta depois = servico.sacar(0, new BigDecimal("30.00"));

        assertThat(depois.saldo()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("depositar em conta inexistente e rejeitado")
    void depositoEmContaInexistenteEhRejeitado() {
        assertThrows(ContaNaoEncontradaException.class,
                () -> servico.depositar(0, new BigDecimal("10.00")));
    }

    // ---------- relogio de Lamport aplicado (o que vale os 3 pontos) ----------

    @Test
    @DisplayName("abrir conta registra evento CRIAR_CONTA carimbado com 1")
    void abrirRegistraEventoCarimbado() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));

        assertThat(registro.eventos).hasSize(1);
        Evento evento = registro.eventos.get(0);
        assertThat(evento.tipo()).isEqualTo("CRIAR_CONTA");
        assertThat(evento.carimbo()).isEqualTo(new CarimboLamport(1));
    }

    @Test
    @DisplayName("operacoes sucessivas avancam o relogio: carimbos 1, 2 e 3")
    void operacoesSucessivasAvancamORelogio() {
        servico.abrir(0, "Ana", new BigDecimal("100.00"));
        servico.depositar(0, new BigDecimal("10.00"));
        servico.sacar(0, new BigDecimal("5.00"));

        assertThat(registro.eventos).extracting(Evento::carimbo)
                .containsExactly(new CarimboLamport(1), new CarimboLamport(2), new CarimboLamport(3));

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

    /** Fake: guarda os eventos numa lista para o teste poder afirmar sobre eles. */
    private static class RegistroEmLista implements RegistroEventos {
        final List<Evento> eventos = new ArrayList<>();

        @Override
        public Evento registrar(String tipo, Carimbo carimbo, Map<String, Object> detalhes) {
            Evento evento = new Evento("teste", tipo, carimbo, Instant.now(), detalhes);
            eventos.add(evento);
            return evento;
        }
    }
}
