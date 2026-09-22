package br.pucminas.iceibank.modelo.relogio;

import java.util.List;
import java.util.Objects;

/**
 * Um instante no tempo logico: um contador por agencia.
 *
 * Guarda `List<Integer>` e nao `int[]` de proposito. Record com array nao tem
 * igualdade por valor (array compara por identidade), e o carimbo precisa de
 * equals/hashCode para viver em Set e Map — inclusive no teste de concorrencia,
 * que conta vetores distintos. `List.copyOf` no construtor compacto garante que
 * ninguem altera o carimbo depois de criado.
 */
public record CarimboVetorial(List<Integer> valores) {

    public CarimboVetorial {
        Objects.requireNonNull(valores, "valores do carimbo vetorial");
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("carimbo vetorial nao pode ser vazio");
        }
        valores = List.copyOf(valores);          // copia defensiva: o record fica imutavel de verdade
    }

    public int tamanho() {
        return valores.size();
    }

    public int valorDe(int idAgencia) {
        return valores.get(idAgencia);
    }

    /**
     * A relacao causal entre dois carimbos, comparando posicao a posicao.
     *
     * Se A[i] <= B[i] em TODA posicao, entao A aconteceu-antes de B: nao ha como B
     * ter contribuido para A. Se nenhum dos dois domina o outro, os eventos sao
     * CONCORRENTES — cada um sabe de algo que o outro nao sabe.
     *
     * Esta e a pergunta que o relogio de Lamport nao respondia: com um inteiro so,
     * ts(A) < ts(B) pode significar "A causou B" ou "sao independentes", e nao ha
     * como distinguir.
     */
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
        return valores.toString();               // [2, 1, 0] — o formato que a linha do tempo imprime
    }
}
