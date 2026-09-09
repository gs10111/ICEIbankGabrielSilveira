package br.pucminas.iceibank.aplicacao.transferencia;

import br.pucminas.iceibank.aplicacao.porta.AgenciaRemota;
import br.pucminas.iceibank.aplicacao.porta.AgenciaRemotaIndisponivelException;
import br.pucminas.iceibank.aplicacao.porta.ChaveIdempotenciaConflitanteException;
import br.pucminas.iceibank.aplicacao.porta.RegistroEventos;
import br.pucminas.iceibank.dominio.conta.Conta;
import br.pucminas.iceibank.dominio.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.dominio.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.dominio.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.dominio.conta.ValorInvalidoException;
import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.particao.Particionador;
import br.pucminas.iceibank.dominio.relogio.Carimbo;
import br.pucminas.iceibank.dominio.relogio.CarimboLamport;
import br.pucminas.iceibank.dominio.relogio.RelogioLamport;
import br.pucminas.iceibank.infra.memoria.ContaRepositorioEmMemoria;
import br.pucminas.iceibank.infra.memoria.RegistroIdempotenciaEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferenciaServiceTest {

    private static final int ID_AGENCIA = 0;

    private ContaRepositorioEmMemoria repositorio;
    private RegistroEmLista registro;
    private AgenciaRemotaFalsa agenciaRemota;
    private TransferenciaService servico;

    @BeforeEach
    void montar() {
        repositorio = new ContaRepositorioEmMemoria();
        registro = new RegistroEmLista();
        agenciaRemota = new AgenciaRemotaFalsa();
        servico = new TransferenciaService(ID_AGENCIA, new Particionador(3), repositorio,
                new RelogioLamport(), registro, agenciaRemota);

        repositorio.salvar(new Conta(0, "Ana", new BigDecimal("100.00")));   // 0 % 3 == 0
        repositorio.salvar(new Conta(3, "Caio", new BigDecimal("20.00")));   // 3 % 3 == 0
    }

    private OrdemDeTransferencia ordem(int origem, int destino, String valor) {
        return new OrdemDeTransferencia(null, origem, destino, new BigDecimal(valor));
    }

    @Nested
    @DisplayName("mesma agencia")
    class Local {

        @Test
        @DisplayName("move o dinheiro entre as duas contas locais")
        void moveODinheiro() {
            Recibo recibo = servico.executar(ordem(0, 3, "30.00"));

            assertThat(recibo.local()).isTrue();
            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");
            assertThat(repositorio.buscar(3).orElseThrow().saldo()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("gera DOIS eventos locais (carimbos 1 e 2), sem envio nem recebimento")
        void geraDoisEventosLocais() {
            servico.executar(ordem(0, 3, "30.00"));

            assertThat(registro.eventos).extracting(Evento::tipo)
                    .containsExactly("TRANSFERENCIA_DEBITO", "TRANSFERENCIA_CREDITO");
            assertThat(registro.eventos).extracting(Evento::carimbo)
                    .containsExactly(new CarimboLamport(1), new CarimboLamport(2));
            assertThat(agenciaRemota.chamadas).isZero();
        }

        @Test
        @DisplayName("destino inexistente e recusado ANTES do debito: nada e movido")
        void destinoInexistenteNaoDebita() {
            assertThrows(ContaNaoEncontradaException.class, () -> servico.executar(ordem(0, 6, "30.00")));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("100.00");
            assertThat(registro.eventos).isEmpty();
        }
    }

    @Nested
    @DisplayName("entre agencias")
    class EntreAgencias {

        @Test
        @DisplayName("debita local e credita remoto usando aoEnviar (regra 2 de Lamport)")
        void debitaLocalECreditaRemoto() {
            Recibo recibo = servico.executar(ordem(0, 1, "30.00"));   // conta 1 -> agencia 1

            assertThat(recibo.local()).isFalse();
            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");
            assertThat(agenciaRemota.chamadas).isEqualTo(1);
            assertThat(agenciaRemota.ultimaAgenciaDestino).isEqualTo(1);
            // debito = carimbo 1 (eventoLocal); envio = carimbo 2 (aoEnviar)
            assertThat(agenciaRemota.ultimoCarimbo).isEqualTo(new CarimboLamport(2));
        }

        @Test
        @DisplayName("LIMITACAO CONHECIDA: destino fora do ar deixa o debito aplicado, sem reversao")
        void falhaRemotaNaoReverteODebito() {
            agenciaRemota.forcarIndisponibilidade();

            assertThrows(AgenciaRemotaIndisponivelException.class, () -> servico.executar(ordem(0, 1, "30.00")));

            // O dinheiro "sumiu": saiu da origem e nunca chegou ao destino.
            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");

            assertThat(registro.eventos).extracting(Evento::tipo)
                    .containsExactly("TRANSFERENCIA_DEBITO", "TRANSFERENCIA_FALHOU");
        }
    }

    @Nested
    @DisplayName("credito remoto (regra 3 de Lamport)")
    class CreditoRemoto {

        @Test
        @DisplayName("ajusta o relogio para max(local, recebido) + 1 e credita a conta")
        void ajustaRelogioECredita() {
            servico.executar(ordem(0, 3, "10.00"));    // relogio local vai a 2

            Conta conta = servico.creditarRemoto(0, new BigDecimal("5.00"), new CarimboLamport(9), 2);

            assertThat(conta.saldo()).isEqualByComparingTo("95.00");
            Evento credito = registro.eventos.get(registro.eventos.size() - 1);
            assertThat(credito.tipo()).isEqualTo("TRANSFERENCIA_CREDITO_REMOTO");
            assertThat(credito.carimbo()).isEqualTo(new CarimboLamport(10));   // max(2, 9) + 1
        }
    }

    @Nested
    @DisplayName("validacoes")
    class Validacoes {

        @Test
        @DisplayName("origem que nao e desta agencia e recusada")
        void origemDeOutraAgencia() {
            assertThrows(ContaNaoPertenceAgenciaException.class, () -> servico.executar(ordem(1, 0, "10.00")));
        }

        @Test
        @DisplayName("origem igual ao destino e recusada")
        void origemIgualDestino() {
            assertThrows(ValorInvalidoException.class, () -> servico.executar(ordem(0, 0, "10.00")));
        }

        @Test
        @DisplayName("saldo insuficiente e recusado pela propria Conta")
        void saldoInsuficiente() {
            assertThrows(SaldoInsuficienteException.class, () -> servico.executar(ordem(0, 3, "999.00")));
        }
    }

    @Nested
    @DisplayName("idempotencia (funcionalidade adicional 2)")
    class Idempotencia {

        private Transferir transferir;

        @BeforeEach
        void decorar() {
            transferir = new TransferenciaIdempotente(servico, new RegistroIdempotenciaEmMemoria());
        }

        @Test
        @DisplayName("reenvio com a mesma chave NAO debita duas vezes")
        void reenvioNaoDebitaDuasVezes() {
            OrdemDeTransferencia mesma = new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("30.00"));

            transferir.executar(mesma);
            Recibo segunda = transferir.executar(mesma);

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");
            assertThat(segunda.reenvio()).isTrue();
            assertThat(registro.eventos).hasSize(2);   // so a primeira execucao gerou eventos
        }

        @Test
        @DisplayName("chaves diferentes sao operacoes diferentes e ambas sao aplicadas")
        void chavesDiferentesAplicamDuasVezes() {
            transferir.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("30.00")));
            transferir.executar(new OrdemDeTransferencia("chave-2", 0, 3, new BigDecimal("30.00")));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("sem chave, cada requisicao e aplicada: o cliente abriu mao da garantia")
        void semChaveAplicaSempre() {
            transferir.executar(ordem(0, 3, "30.00"));
            transferir.executar(ordem(0, 3, "30.00"));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("mesma chave com dados diferentes e conflito, nao reenvio")
        void mesmaChaveComDadosDiferentesEhConflito() {
            transferir.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("10.00")));

            assertThrows(ChaveIdempotenciaConflitanteException.class,
                    () -> transferir.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("999.00"))));
        }

        @Test
        @DisplayName("operacao que falha nao e memorizada: pode ser retentada")
        void falhaNaoEhMemorizada() {
            OrdemDeTransferencia paraOutraAgencia =
                    new OrdemDeTransferencia("chave-9", 0, 1, new BigDecimal("30.00"));
            agenciaRemota.forcarIndisponibilidade();
            assertThrows(AgenciaRemotaIndisponivelException.class, () -> transferir.executar(paraOutraAgencia));

            agenciaRemota.voltarAoAr();
            assertThat(transferir.executar(paraOutraAgencia).reenvio()).isFalse();
        }
    }

    // ------------------------------------------------------------------ fakes

    private static class AgenciaRemotaFalsa implements AgenciaRemota {
        int chamadas;
        int ultimaAgenciaDestino;
        Carimbo ultimoCarimbo;
        private boolean noAr = true;

        void forcarIndisponibilidade() {
            noAr = false;
        }

        void voltarAoAr() {
            noAr = true;
        }

        @Override
        public void creditar(int idAgenciaDestino, int idConta, BigDecimal valor,
                             Carimbo carimbo, int agenciaOrigem) {
            if (!noAr) {
                throw new AgenciaRemotaIndisponivelException("agencia " + idAgenciaDestino + " fora do ar", null);
            }
            chamadas++;
            ultimaAgenciaDestino = idAgenciaDestino;
            ultimoCarimbo = carimbo;
        }
    }

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
