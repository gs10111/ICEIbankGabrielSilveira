package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.CarimboLamport;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeIdempotencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferenciaServiceTest {

    private static final int ID_AGENCIA = 0;

    private static final AgenciaProperties PROPRIEDADES = new AgenciaProperties(
            ID_AGENCIA, 3,
            List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
            "target/dados-de-teste");

    private static final SegurancaProperties SEGURANCA = new SegurancaProperties(
            "segredo-de-teste-com-mais-de-32-caracteres", 900, "token-entre-agencias-de-teste");

    private ContaRepositorio repositorio;
    private RegistroEmLista registro;
    private AgenciaRemotaFalsa agenciaRemota;
    private TransferenciaService servico;

    @BeforeEach
    void montar() {
        repositorio = new ContaRepositorio();
        registro = new RegistroEmLista();
        agenciaRemota = new AgenciaRemotaFalsa();
        servico = new TransferenciaService(PROPRIEDADES, new Particionador(3), repositorio,
                new RelogioLamport(), registro, agenciaRemota, new RegistroDeIdempotencia());

        repositorio.inserir(new Conta(0, "Ana", new BigDecimal("100.00")));   // 0 % 3 == 0
        repositorio.inserir(new Conta(3, "Caio", new BigDecimal("20.00")));   // 3 % 3 == 0
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

        @Test
        @DisplayName("reenvio com a mesma chave NAO debita duas vezes")
        void reenvioNaoDebitaDuasVezes() {
            OrdemDeTransferencia mesma = new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("30.00"));

            servico.executar(mesma);
            Recibo segunda = servico.executar(mesma);

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");
            assertThat(segunda.reenvio()).isTrue();
            assertThat(registro.eventos).hasSize(2);   // so a primeira execucao gerou eventos
        }

        @Test
        @DisplayName("chaves diferentes sao operacoes diferentes e ambas sao aplicadas")
        void chavesDiferentesAplicamDuasVezes() {
            servico.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("30.00")));
            servico.executar(new OrdemDeTransferencia("chave-2", 0, 3, new BigDecimal("30.00")));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("sem chave, cada requisicao e aplicada: o cliente abriu mao da garantia")
        void semChaveAplicaSempre() {
            servico.executar(ordem(0, 3, "30.00"));
            servico.executar(ordem(0, 3, "30.00"));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("a chave vale por conta de origem: duas contas podem usar a mesma")
        void chaveDeIdempotenciaEhPorContaDeOrigem() {
            repositorio.inserir(new Conta(6, "Elis", new BigDecimal("50.00")));   // 6 % 3 == 0

            servico.executar(new OrdemDeTransferencia("op-1", 0, 3, new BigDecimal("10.00")));
            Recibo daOutraConta = servico.executar(new OrdemDeTransferencia("op-1", 6, 3, new BigDecimal("10.00")));

            assertThat(daOutraConta.reenvio()).isFalse();
            assertThat(repositorio.buscar(6).orElseThrow().saldo()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("mesma chave com dados diferentes e conflito, nao reenvio")
        void mesmaChaveComDadosDiferentesEhConflito() {
            servico.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("10.00")));

            assertThrows(ChaveIdempotenciaConflitanteException.class,
                    () -> servico.executar(new OrdemDeTransferencia("chave-1", 0, 3, new BigDecimal("999.00"))));
        }

        @Test
        @DisplayName("operacao que falha nao e memorizada: pode ser retentada")
        void falhaNaoEhMemorizada() {
            OrdemDeTransferencia paraOutraAgencia =
                    new OrdemDeTransferencia("chave-9", 0, 1, new BigDecimal("30.00"));
            agenciaRemota.forcarIndisponibilidade();
            assertThrows(AgenciaRemotaIndisponivelException.class, () -> servico.executar(paraOutraAgencia));

            agenciaRemota.voltarAoAr();
            assertThat(servico.executar(paraOutraAgencia).reenvio()).isFalse();
        }
    }

    @Nested
    @DisplayName("destino remoto e particao")
    class DestinoRemoto {

        @Test
        @DisplayName("destino que nao existe na outra agencia NAO debita a origem")
        void destinoRemotoInexistenteNaoDebita() {
            agenciaRemota.contasQueExistemLa.remove(1);

            assertThrows(ContaNaoEncontradaException.class, () -> servico.executar(ordem(0, 1, "30.00")));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("100.00");
            assertThat(agenciaRemota.chamadas).isZero();
        }

        @Test
        @DisplayName("agencia fora do ar AINDA produz a falha conhecida da Parte D")
        void agenciaForaDoArAindaDebitaSemReverter() {
            // Guarda-costas da pre-checagem acima: ela NAO pode transformar a falha
            // exigida pelo roteiro num erro limpo antes do debito.
            agenciaRemota.forcarIndisponibilidade();

            assertThrows(AgenciaRemotaIndisponivelException.class, () -> servico.executar(ordem(0, 1, "30.00")));

            assertThat(repositorio.buscar(0).orElseThrow().saldo()).isEqualByComparingTo("70.00");
            assertThat(registro.eventos).extracting(Evento::tipo)
                    .containsExactly("TRANSFERENCIA_DEBITO", "TRANSFERENCIA_FALHOU");
        }

        @Test
        @DisplayName("creditar-remoto recusa conta que nao e desta agencia (400, nao 404)")
        void creditarRemotoRecusaContaDeOutraAgencia() {
            // conta 1 pertence a agencia 1; esta e a agencia 0
            assertThrows(ContaNaoPertenceAgenciaException.class,
                    () -> servico.creditarRemoto(1, new BigDecimal("10.00"), new CarimboLamport(9), 1));
        }
    }

    // ------------------------------------------------------------------ fakes

    private static class AgenciaRemotaFalsa extends AgenciaRemota {
        final Set<Integer> contasQueExistemLa = new HashSet<>(Set.of(1, 2));
        int chamadas;
        int ultimaAgenciaDestino;
        Carimbo ultimoCarimbo;
        private boolean noAr = true;

        AgenciaRemotaFalsa() {
            super(PROPRIEDADES, null, SEGURANCA);
        }

        void forcarIndisponibilidade() {
            noAr = false;
        }

        void voltarAoAr() {
            noAr = true;
        }

        @Override
        public Optional<ContaRemota> consultar(int idAgencia, int idConta) {
            if (!noAr) {
                throw new AgenciaRemotaIndisponivelException("agencia " + idAgencia + " fora do ar", null);
            }
            return contasQueExistemLa.contains(idConta)
                    ? Optional.of(new ContaRemota(idConta, "Titular " + idConta, new BigDecimal("500.00"), idAgencia))
                    : Optional.empty();
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

    private static class RegistroEmLista extends RegistroDeEventos {
        final List<Evento> eventos = new ArrayList<>();

        RegistroEmLista() {
            super(PROPRIEDADES);
        }

        @Override
        public Evento registrar(String tipo, Carimbo carimbo, Map<String, Object> detalhes) {
            Evento evento = new Evento("teste", tipo, carimbo, Instant.now(), detalhes);
            eventos.add(evento);
            return evento;
        }
    }
}
