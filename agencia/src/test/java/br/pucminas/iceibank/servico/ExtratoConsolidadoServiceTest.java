package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FUNCIONALIDADE ADICIONAL 3 — extrato consolidado.
 *
 * O ponto que estes testes travam nao e a soma: e o que acontece quando UMA das
 * agencias nao responde. O total precisa sair rotulado como parcial, nunca como
 * um numero errado apresentado como certo.
 */
class ExtratoConsolidadoServiceTest {

    private static final int ID_AGENCIA = 0;

    private static final AgenciaProperties PROPRIEDADES = new AgenciaProperties(
            ID_AGENCIA, 3,
            List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
            "target/dados-de-teste");

    private static final SegurancaProperties SEGURANCA = new SegurancaProperties(
            "segredo-de-teste-com-mais-de-32-caracteres", 900, "token-entre-agencias-de-teste");

    private ContaRepositorio repositorio;
    private AgenciaRemotaFalsa agenciaRemota;
    private ExtratoConsolidadoService servico;

    @BeforeEach
    void montar() {
        repositorio = new ContaRepositorio();
        agenciaRemota = new AgenciaRemotaFalsa();
        servico = new ExtratoConsolidadoService(PROPRIEDADES, new Particionador(3), repositorio, agenciaRemota);

        repositorio.salvar(new Conta(0, "Ana Souza", new BigDecimal("1000.00")));  // 0 % 3 == 0, local
        repositorio.salvar(new Conta(3, "Diego Melo", new BigDecimal("300.00")));  // 3 % 3 == 0, local
    }

    @Test
    @DisplayName("soma contas locais e remotas da mesma titular")
    void somaLocalComRemoto() {
        agenciaRemota.saldos.put(4, new BigDecimal("250.00"));   // 4 % 3 == 1, agencia 1

        ExtratoConsolidadoService.Extrato extrato = servico.consolidar(List.of(0, 4));

        assertThat(extrato.total()).isEqualByComparingTo("1250.00");
        assertThat(extrato.consistente()).isTrue();
        assertThat(extrato.contas()).extracting(ExtratoConsolidadoService.ItemDoExtrato::agencia)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("agencia fora do ar: o item sai indisponivel e o total vira parcial")
    void agenciaForaDoArTornaOExtratoParcial() {
        agenciaRemota.saldos.put(4, new BigDecimal("250.00"));
        agenciaRemota.forcarIndisponibilidade();

        ExtratoConsolidadoService.Extrato extrato = servico.consolidar(List.of(0, 4));

        assertThat(extrato.consistente()).isFalse();
        assertThat(extrato.total()).isEqualByComparingTo("1000.00");   // so o que deu para ler
        assertThat(extrato.contas()).filteredOn(item -> item.id() == 4)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.disponivel()).isFalse();
                    assertThat(item.saldo()).isNull();
                });
    }

    @Test
    @DisplayName("conta que nao existe em lugar nenhum tambem torna o extrato parcial")
    void contaInexistenteTornaOExtratoParcial() {
        ExtratoConsolidadoService.Extrato extrato = servico.consolidar(List.of(0, 999));

        assertThat(extrato.consistente()).isFalse();
        assertThat(extrato.total()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("ids repetidos nao contam duas vezes")
    void idsRepetidosNaoDobramOTotal() {
        assertThat(servico.consolidar(List.of(0, 0, 3)).total()).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("idsDoTitularLocal acha as contas do mesmo nome nesta agencia")
    void achaContasDoMesmoTitularLocalmente() {
        repositorio.salvar(new Conta(6, "Ana Souza", new BigDecimal("70.00")));   // 6 % 3 == 0

        assertThat(servico.idsDoTitularLocal("ana souza")).containsExactlyInAnyOrder(0, 6);
    }

    // ------------------------------------------------------------------ fake

    private static class AgenciaRemotaFalsa extends AgenciaRemota {
        final java.util.Map<Integer, BigDecimal> saldos = new java.util.HashMap<>();
        private final Set<Integer> foraDoAr = new HashSet<>();

        AgenciaRemotaFalsa() {
            super(PROPRIEDADES, null, SEGURANCA);
        }

        void forcarIndisponibilidade() {
            foraDoAr.addAll(Set.of(0, 1, 2));
        }

        @Override
        public Optional<ContaRemota> consultar(int idAgencia, int idConta) {
            if (foraDoAr.contains(idAgencia)) {
                throw new AgenciaRemotaIndisponivelException("agencia " + idAgencia + " fora do ar", null);
            }
            return Optional.ofNullable(saldos.get(idConta))
                    .map(saldo -> new ContaRemota(idConta, "Ana Souza", saldo, idAgencia));
        }
    }
}
