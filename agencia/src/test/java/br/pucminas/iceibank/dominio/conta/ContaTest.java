package br.pucminas.iceibank.dominio.conta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

}
