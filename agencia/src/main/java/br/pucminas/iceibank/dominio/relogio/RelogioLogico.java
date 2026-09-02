package br.pucminas.iceibank.dominio.relogio;

/**
 * Relogio logico de um processo: carimba eventos respeitando causalidade.
 *
 * Sprint 1: RelogioLamport (contador inteiro).
 * Sprint 2: RelogioVetorial (um contador por processo).
 *
 * Os casos de uso dependem desta interface, nunca da implementacao —
 * por isso a troca do Sprint 2 nao os afeta.
 */
public interface RelogioLogico {

    /** Regra 1: antes de qualquer evento local, incrementa o contador. */
    Carimbo eventoLocal();

    /** Regra 2: ao enviar mensagem, incrementa e anexa o valor. */
    Carimbo aoEnviar();

    /** Regra 3: ao receber com timestamp t, ajusta para max(local, t) + 1. */
    Carimbo aoReceber(Carimbo recebido);
}
