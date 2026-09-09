package br.pucminas.iceibank.aplicacao.porta;

import br.pucminas.iceibank.aplicacao.transferencia.OrdemDeTransferencia;
import br.pucminas.iceibank.aplicacao.transferencia.Recibo;

import java.util.function.Supplier;

/**
 * Porta de saida da idempotencia (FUNCIONALIDADE ADICIONAL 2).
 *
 * A operacao e "executar no maximo uma vez por chave" — nao "consultar" e depois
 * "gravar". Se fossem dois metodos, duas requisicoes simultaneas com a mesma chave
 * passariam as duas pela consulta antes de qualquer gravacao (corrida classica).
 * Com um metodo so, o adapter garante atomicidade (ConcurrentHashMap.computeIfAbsent).
 */
public interface RegistroIdempotencia {

    /**
     * Executa a operacao se a chave for nova; devolve o recibo guardado se ja foi usada.
     *
     * @throws ChaveIdempotenciaConflitanteException se a chave ja existe com dados de negocio diferentes
     */
    Recibo executarUmaVez(String chave, OrdemDeTransferencia ordem, Supplier<Recibo> operacao);
}
