package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;

import java.math.BigDecimal;
import java.util.List;

public record ContaResposta(int id, String nomeAluno, BigDecimal saldo, int agencia, List<Integer> vetorAtual) {
    public static ContaResposta de(Conta conta, int agencia, CarimboVetorial vetorAtual) {
        return new ContaResposta(conta.id(), conta.nome(), conta.saldo(), agencia, vetorAtual.valores());
    }
}
