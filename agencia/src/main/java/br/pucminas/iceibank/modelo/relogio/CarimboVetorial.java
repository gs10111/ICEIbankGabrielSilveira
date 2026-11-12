package br.pucminas.iceibank.modelo.relogio;

import java.util.List;
import java.util.Objects;

public record CarimboVetorial(List<Integer> valores) {
    public CarimboVetorial {
        Objects.requireNonNull(valores, "valores do carimbo vetorial");
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("carimbo vetorial nao pode ser vazio");
        }
        valores = List.copyOf(valores);
        if (valores.stream().anyMatch(v -> v < 0)) {
            throw new IllegalArgumentException("carimbo vetorial com posicao negativa: " + valores);
        }
    }

    public int tamanho() {
        return valores.size();
    }

    public int valorDe(int idAgencia) {
        return valores.get(idAgencia);
    }

    public static Relacao comparar(CarimboVetorial primeiro, CarimboVetorial segundo) {
        if (primeiro.tamanho() != segundo.tamanho()) {
            throw new IllegalArgumentException(
                    "carimbos de malhas diferentes: " + primeiro.tamanho() + " x " + segundo.tamanho());
        }

        boolean primeiroNuncaMaior = true;
        boolean segundoNuncaMaior = true;
        for (int i = 0; i < primeiro.tamanho(); i++) {
            if (primeiro.valorDe(i) > segundo.valorDe(i)) primeiroNuncaMaior = false;
            if (segundo.valorDe(i) > primeiro.valorDe(i)) segundoNuncaMaior = false;
        }

        if (primeiroNuncaMaior && segundoNuncaMaior) return Relacao.IGUAIS;
        if (primeiroNuncaMaior) return Relacao.ANTES;
        if (segundoNuncaMaior) return Relacao.DEPOIS;
        return Relacao.CONCORRENTES;
    }

    @Override
    public String toString() {
        return valores.toString();
    }
}
