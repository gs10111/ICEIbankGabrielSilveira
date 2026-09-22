package br.pucminas.iceibank.modelo.relogio;

import java.util.Arrays;

/**
 * Relogio vetorial — Sprint 2, Parte B. Substitui o relogio de Lamport do Sprint 1.
 *
 * Um contador por agencia. Cada agencia so incrementa a PROPRIA posicao; as outras
 * posicoes so mudam quando chega mensagem de fora. E isso que permite responder se
 * dois eventos sao concorrentes — ver {@link CarimboVetorial#comparar}.
 *
 * As tres regras:
 *   1. evento local    -> incrementa a propria posicao
 *   2. ao enviar       -> incrementa a propria posicao e anexa o vetor inteiro a mensagem
 *   3. ao receber V    -> vetor[i] = max(vetor[i], V[i]) em cada posicao, DEPOIS incrementa a propria
 *
 * `synchronized` pelo mesmo motivo do RelogioVetorial do Sprint 1: `vetor[i] += 1` e
 * um ler-modificar-escrever, e o Tomcat atende cada requisicao numa thread do pool.
 * No Sprint 1 o teste de 100 threads pegou o contador perdendo uma escrita; aqui o
 * teste equivalente existe desde o primeiro commit.
 */
public class RelogioVetorial {

    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(int idAgencia, int totalDeAgencias) {
        this(idAgencia, new int[exigirMalhaValida(idAgencia, totalDeAgencias)]);
    }

    /**
     * Restaura o relogio num vetor ja alcancado.
     *
     * Mesmo motivo do Sprint 1 (commit e1bb956): o .jsonl e append-only e sobrevive
     * ao restart, o vetor em memoria nao. Sem restaurar, a agencia volta zerada sobre
     * um log que ja tem posicoes maiores e passa a produzir empates falsos.
     */
    public RelogioVetorial(int idAgencia, CarimboVetorial inicial) {
        this(idAgencia, paraArray(inicial));
    }

    private RelogioVetorial(int idAgencia, int[] vetorInicial) {
        exigirMalhaValida(idAgencia, vetorInicial.length);
        this.idAgencia = idAgencia;
        this.vetor = vetorInicial;
    }

    /** Le o vetor SEM avanca-lo. Uma leitura nao e evento: nenhuma das tres regras se aplica. */
    public synchronized CarimboVetorial valorAtual() {
        return carimbo();
    }

    /** Regra 1. */
    public synchronized CarimboVetorial eventoLocal() {
        vetor[idAgencia] += 1;
        return carimbo();
    }

    /** Regra 2. Identica a regra 1 no efeito; separada porque o vocabulario do dominio e outro. */
    public synchronized CarimboVetorial aoEnviar() {
        vetor[idAgencia] += 1;
        return carimbo();
    }

    /** Regra 3. */
    public synchronized CarimboVetorial aoReceber(CarimboVetorial recebido) {
        if (recebido.tamanho() != vetor.length) {
            throw new IllegalArgumentException(
                    "vetor recebido tem " + recebido.tamanho() + " posicoes; esta malha tem " + vetor.length);
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], recebido.valorDe(i));
        }
        vetor[idAgencia] += 1;                   // o incremento vem DEPOIS do max
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
