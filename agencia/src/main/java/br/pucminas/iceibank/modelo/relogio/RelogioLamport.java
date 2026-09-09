package br.pucminas.iceibank.modelo.relogio;

public class RelogioLamport {
    private int contador = 0;

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
