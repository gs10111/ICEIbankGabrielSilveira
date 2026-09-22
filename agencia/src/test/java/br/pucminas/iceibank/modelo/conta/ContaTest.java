package br.pucminas.iceibank.modelo.conta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class ContaTest {

    private Conta conta;

    @BeforeEach
    void criarContaComCemReais() {
        conta = new Conta(0, "Ana", new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("saque diminui o saldo pelo valor sacado")
    void saqueDiminuiSaldo() {

        conta.sacar(new BigDecimal("30.00"));

        assertThat(conta.saldo()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("ao tentar sacar um valor maior do que o saldo lancar excessao")
    void lancarSaldoInsuficienteException() {

        assertThrows(SaldoInsuficienteException.class, () -> conta.sacar(new BigDecimal("150.00")));

    }

    @Test
    @DisplayName("sacar exatamente o saldo e permitido e zera a conta")
    void saqueDoSaldoTotalZeraAConta() {
        conta.sacar(new BigDecimal("100.00"));

        assertThat(conta.saldo()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("sacar valor zero ou negativo e rejeitado")
    void saqueDeValorInvalidoEhRejeitado() {
        assertThrows(ValorInvalidoException.class, () -> conta.sacar(BigDecimal.ZERO));
        assertThrows(ValorInvalidoException.class, () -> conta.sacar(new BigDecimal("-50.00")));
    }

    @Test
    @DisplayName("deposito aumenta o saldo pelo valor depositado")
    void depositoAumentaSaldo() {
        conta.depositar(new BigDecimal("25.50"));

        assertThat(conta.saldo()).isEqualByComparingTo("125.50");
    }

    @Test
    @DisplayName("deposito de valor zero ou negativo e rejeitado")
    void depositoDeValorInvalidoEhRejeitado() {
        assertThrows(ValorInvalidoException.class, () -> conta.depositar(BigDecimal.ZERO));
        assertThrows(ValorInvalidoException.class, () -> conta.depositar(new BigDecimal("-10.00")));
    }


    @Test
    @DisplayName("cem depositos simultaneos somam os cem: nenhuma escrita se perde")
    void depositoEhSeguroSobConcorrencia() throws InterruptedException {
        // Mesma corrida do contador do relogio: saldo = saldo.add(...) le, soma e
        // escreve. Sem protecao, duas threads leem o mesmo saldo e uma sobrescreve
        // a outra — lost update.
        int totalThreads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(totalThreads);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    conta.depositar(BigDecimal.ONE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        chegada.await();
        pool.shutdown();

        assertThat(conta.saldo())
                .as("depositos perdidos: o saldo nao e thread-safe")
                .isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("cem saques simultaneos de conta com cinquenta: cinquenta passam, saldo nunca fica negativo")
    void saqueNaoDeixaSaldoNegativoSobConcorrencia() throws InterruptedException {
        // O saque e um check-then-act: confere o saldo e so depois subtrai. Sem
        // protecao, duas threads passam pela mesma checagem e a conta fica negativa
        // — o invariante que Conta existe para garantir.
        Conta comCinquenta = new Conta(1, "Bruno", new BigDecimal("50.00"));
        int totalThreads = 100;
        AtomicInteger aceitos = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(totalThreads);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    comCinquenta.sacar(BigDecimal.ONE);
                    aceitos.incrementAndGet();
                } catch (SaldoInsuficienteException esperado) {
                    // metade das threads tem de bater aqui
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        chegada.await();
        pool.shutdown();

        assertThat(aceitos.get())
                .as("saques a mais foram aceitos: o check-then-act nao e atomico")
                .isEqualTo(50);
        assertThat(comCinquenta.saldo())
                .as("saldo negativo: o invariante de Conta foi violado")
                .isEqualByComparingTo("0.00");
    }

}
