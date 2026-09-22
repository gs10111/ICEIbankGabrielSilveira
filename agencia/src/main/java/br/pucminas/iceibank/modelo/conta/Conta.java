package br.pucminas.iceibank.modelo.conta;

import java.math.BigDecimal;

/**
 * Uma conta e o seu saldo.
 *
 * `synchronized` nos tres metodos que tocam o saldo, pelo mesmo motivo do
 * RelogioVetorial: o Tomcat atende cada requisicao numa thread do pool, e
 * `saldo = saldo.add(...)` e um ler-modificar-escrever. Sem isto, dois depositos
 * simultaneos perdem um (lost update) e dois saques passam pela MESMA checagem de
 * saldo e deixam a conta negativa — violando o unico invariante que esta classe
 * existe para garantir.
 *
 * O lock e por instancia, ou seja, por conta: duas contas diferentes nao se
 * esperam. Uma transferencia continua NAO sendo atomica sobre as duas pontas —
 * isso e problema de transacao distribuida, nao de lock local.
 */
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

    public synchronized BigDecimal saldo() {
        return saldo;
    }

    public synchronized void depositar(BigDecimal valor) {
        exigirValorPositivo(valor);
        this.saldo = this.saldo.add(valor);
    }

    public synchronized void sacar(BigDecimal valor) {
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
