package br.pucminas.iceibank.dominio.conta;

import java.math.BigDecimal;

public final class Conta {

    private final int id;
    private final String nome;
    private BigDecimal saldo;

    public Conta(int id, String nome, BigDecimal saldoInicial) {
        this.id = id;
        this.nome = nome;
        this.saldo = saldoInicial;
    }

    public int id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public BigDecimal saldo() {
        return saldo;
    }

    public void depositar(BigDecimal valor) {
        exigirValorPositivo(valor);
        this.saldo = this.saldo.add(valor);
    }

    public void sacar(BigDecimal valor) {
        exigirValorPositivo(valor);
        if (saldo.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException("saldo insuficiente: saldo=" + saldo + " valor=" + valor);
        }
        this.saldo = this.saldo.subtract(valor);
    }

    private static void exigirValorPositivo(BigDecimal valor) {
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValorInvalidoException("valor deve ser positivo: " + valor);
        }
    }
}
