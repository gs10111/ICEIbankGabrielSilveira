package br.pucminas.iceibank.dominio.relogio;

public class RelogioLamport {
    private  int contador = 0 ;
    public Carimbo eventoLocal() {
        contador ++;
        return new CarimboLamport(contador);
    }

    private Carimbo aoEnviar(){
        contador++;
        return new CarimboLamport(contador);
    }
}
