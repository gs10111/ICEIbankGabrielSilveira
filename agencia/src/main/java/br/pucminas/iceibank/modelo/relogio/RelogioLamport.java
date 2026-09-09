package br.pucminas.iceibank.modelo.relogio;

public class RelogioLamport implements RelogioLogico {
    private int contador = 0;

    @Override
    public Carimbo eventoLocal() {
        return incrementar();
    }

    @Override
    public Carimbo aoEnviar() {
        return incrementar();
    }

    private synchronized Carimbo incrementar() {
        contador++;
        return new CarimboLamport(contador);
    }

    @Override
    public synchronized Carimbo aoReceber(Carimbo recebido) {
        int valorRecebido = switch (recebido) {
            case CarimboLamport(int valor) -> valor;
        };
        contador = Math.max(contador, valorRecebido) + 1;
        return new CarimboLamport(contador);
    }

}
