package br.pucminas.iceibank.modelo.relogio;

public class RelogioLamport {
    private int contador;

    public RelogioLamport() {
        this(0);
    }

    /**
     * Restaura o relogio num valor ja alcancado.
     *
     * O arquivo de eventos e aberto em APPEND e sobrevive ao restart; o contador,
     * que vive so em memoria, nao. Sem restaurar, os carimbos recomecam em 1 sobre
     * um log que ja tem carimbos maiores, e a linha do tempo unificada passa a
     * mostrar empates que nao sao concorrencia nenhuma — justamente a evidencia
     * que a Parte E pede.
     */
    public RelogioLamport(int valorInicial) {
        if (valorInicial < 0) {
            throw new IllegalArgumentException("carimbo inicial nao pode ser negativo: " + valorInicial);
        }
        this.contador = valorInicial;
    }

    /**
     * Le o contador SEM avanca-lo.
     *
     * Nao e um evento: nenhuma das tres regras de Lamport se aplica a uma leitura.
     * Existe para o /status informar o relogio sem sujar a linha do tempo.
     */
    public synchronized int valorAtual() {
        return contador;
    }

    public Carimbo eventoLocal() {
        return incrementar();
    }

    public Carimbo aoEnviar() {
        return incrementar();
    }

    private synchronized Carimbo incrementar() {
        contador++;
        return new CarimboLamport(contador);
    }

    public synchronized Carimbo aoReceber(Carimbo recebido) {
        int valorRecebido = switch (recebido) {
            case CarimboLamport(int valor) -> valor;
        };
        contador = Math.max(contador, valorRecebido) + 1;
        return new CarimboLamport(contador);
    }

}
