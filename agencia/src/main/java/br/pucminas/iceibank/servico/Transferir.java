package br.pucminas.iceibank.servico;

/**
 * Caso de uso de transferencia.
 *
 * Existe como interface para permitir DECORAR o comportamento sem editar a
 * implementacao (Open/Closed): TransferenciaIdempotente envolve TransferenciaService.
 */
public interface Transferir {
    Recibo executar(OrdemDeTransferencia ordem);
}
