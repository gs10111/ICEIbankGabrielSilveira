package br.pucminas.iceibank.modelo.relogio;

import java.util.Arrays;
import java.util.Optional;

public class RelogioVetorial {
    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(int idAgencia, int totalDeAgencias) {
        this(idAgencia, new int[exigirMalhaValida(idAgencia, totalDeAgencias)]);
    }

    public RelogioVetorial(int idAgencia, CarimboVetorial inicial) {
        this(idAgencia, paraArray(inicial));
    }

    private RelogioVetorial(int idAgencia, int[] vetorInicial) {
        exigirMalhaValida(idAgencia, vetorInicial.length);
        this.idAgencia = idAgencia;
        this.vetor = vetorInicial;
    }

    public synchronized CarimboVetorial valorAtual() {
        return carimbo();
    }

    public synchronized Optional<CarimboVetorial> eventoLocalSeAindaFor(CarimboVetorial esperado) {
        if (!carimbo().equals(esperado)) {
            return Optional.empty();
        }
        vetor[idAgencia] += 1;
        return Optional.of(carimbo());
    }

    public synchronized CarimboVetorial eventoLocal() {
        vetor[idAgencia] += 1;
        return carimbo();
    }

    public synchronized CarimboVetorial aoEnviar() {
        vetor[idAgencia] += 1;
        return carimbo();
    }

    public synchronized CarimboVetorial aoReceber(CarimboVetorial recebido) {
        if (recebido.tamanho() != vetor.length) {
            throw new IllegalArgumentException(
                    "vetor recebido tem " + recebido.tamanho() + " posicoes; esta malha tem " + vetor.length);
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], recebido.valorDe(i));
        }
        vetor[idAgencia] += 1;
        return carimbo();
    }

    public int idAgencia() {
        return idAgencia;
    }

    private CarimboVetorial carimbo() {
        return new CarimboVetorial(Arrays.stream(vetor).boxed().toList());
    }

    private static int exigirMalhaValida(int idAgencia, int totalDeAgencias) {
        if (totalDeAgencias <= 0) {
            throw new IllegalArgumentException("a malha precisa de ao menos uma agencia: " + totalDeAgencias);
        }
        if (idAgencia < 0 || idAgencia >= totalDeAgencias) {
            throw new IllegalArgumentException(
                    "agencia " + idAgencia + " fora de uma malha de " + totalDeAgencias);
        }
        return totalDeAgencias;
    }

    private static int[] paraArray(CarimboVetorial carimbo) {
        int[] copia = new int[carimbo.tamanho()];
        for (int i = 0; i < copia.length; i++) {
            copia[i] = carimbo.valorDe(i);
        }
        return copia;
    }
}
