package br.pucminas.iceibank.dominio.relogio;

public class RelogioLamport {

    public Carimbo eventoLocal() {
        return new CarimboLamport(1);
    }
}
