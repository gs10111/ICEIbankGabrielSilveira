package br.pucminas.iceibank.dominio.particao;

public final class Particionador {

    private final int totalAgencias; // final: nunca muda depois de construído

    public Particionador(int totalAgencias) {
        if (totalAgencias <= 0) {
            throw new IllegalArgumentException("total de agencias deve ser maior que zero: " + totalAgencias);
        }
        this.totalAgencias = totalAgencias;
    }

    public int agenciaResponsavel(int idConta) {
        if (idConta < 0) {
            throw new IllegalArgumentException("id de conta nao pode ser negativo: " + idConta);
        }
        return idConta % totalAgencias;
    }

    public boolean pertenceA(int idConta, int idAgencia) {
        return agenciaResponsavel(idConta) == idAgencia;
    }

}
