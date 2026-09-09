package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.servico.ChaveIdempotenciaConflitanteException;
import br.pucminas.iceibank.servico.RegistroIdempotencia;
import br.pucminas.iceibank.servico.OrdemDeTransferencia;
import br.pucminas.iceibank.servico.Recibo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Adapter da idempotencia. Sprint 4: Redis ou tabela com TTL.
 *
 * Guarda o RECIBO, nao um booleano: o reenvio precisa devolver a MESMA resposta,
 * senao o cliente ve comportamentos diferentes para a mesma requisicao.
 *
 * computeIfAbsent e atomico por chave — resolve a corrida de duas requisicoes
 * simultaneas com a mesma chave, que um `if (contains) ... else put(...)` deixaria passar.
 * Se a operacao lancar, nada e gravado: falha nao deve ser memorizada como sucesso.
 */
public class RegistroIdempotenciaEmMemoria implements RegistroIdempotencia {

    private record Registrado(OrdemDeTransferencia ordem, Recibo recibo) { }

    private final Map<String, Registrado> porChave = new ConcurrentHashMap<>();

    @Override
    public Recibo executarUmaVez(String chave, OrdemDeTransferencia ordem, Supplier<Recibo> operacao) {
        boolean[] executouAgora = {false};

        Registrado registrado = porChave.computeIfAbsent(chave, ignorada -> {
            executouAgora[0] = true;
            return new Registrado(ordem, operacao.get());
        });

        // Mesma chave com dados de negocio diferentes: o cliente errou. Gritar e melhor
        // que devolver calado o recibo de outra operacao.
        if (!registrado.ordem().mesmaOperacaoQue(ordem)) {
            throw new ChaveIdempotenciaConflitanteException(
                    "chave de idempotencia '" + chave + "' ja foi usada para outra transferencia"
                            + " (origem=" + registrado.ordem().idOrigem()
                            + ", destino=" + registrado.ordem().idDestino()
                            + ", valor=" + registrado.ordem().valor() + ")");
        }

        return executouAgora[0] ? registrado.recibo() : registrado.recibo().comoReenvio();
    }
}
