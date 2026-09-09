package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.modelo.relogio.Carimbo;

import java.math.BigDecimal;

/**
 * Porta de saida: falar com OUTRA agencia.
 *
 * Sprint 1: chamada REST direta (AgenciaRemotaRest).
 * Sprint 2: publicacao em topico de mensageria — troca-se o adapter, o caso de uso nao muda.
 * E por isso que TransferenciaService nunca vai importar RestClient.
 */
public interface AgenciaRemota {

    /** @throws AgenciaRemotaIndisponivelException se a agencia de destino nao responder. */
    void creditar(int idAgenciaDestino, int idConta, BigDecimal valor, Carimbo carimbo, int agenciaOrigem);
}
