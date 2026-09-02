package br.pucminas.iceibank.aplicacao.conta;

import br.pucminas.iceibank.aplicacao.porta.RegistroEventos;
import br.pucminas.iceibank.dominio.conta.Conta;
import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.particao.Particionador;
import br.pucminas.iceibank.dominio.relogio.Carimbo;
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
