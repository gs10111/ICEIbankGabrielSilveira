package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;

/**
 * A agencia mudou desde a ultima leitura do app: a operacao feita offline NAO foi
 * aplicada. Carrega o estado atual para o app decidir o que fazer (vira HTTP 409).
 */
public class ConflitoDeSincronizacaoException extends RuntimeException {

    private final transient CarimboVetorial vetorConhecido;
    private final transient CarimboVetorial vetorAtual;
    private final transient Conta contaAtual;

    public ConflitoDeSincronizacaoException(CarimboVetorial vetorConhecido, CarimboVetorial vetorAtual,
                                            Conta contaAtual) {
        super("Conflito detectado: o estado da conta mudou desde a ultima sincronizacao do app.");
        this.vetorConhecido = vetorConhecido;
        this.vetorAtual = vetorAtual;
        this.contaAtual = contaAtual;
    }

    public CarimboVetorial vetorConhecido() {
        return vetorConhecido;
    }

    public CarimboVetorial vetorAtual() {
        return vetorAtual;
    }

    public Conta contaAtual() {
        return contaAtual;
    }
}
