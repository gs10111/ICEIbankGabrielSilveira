package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.servico.ChaveIdempotenciaConflitanteException;
import br.pucminas.iceibank.servico.OrdemDeTransferencia;
import br.pucminas.iceibank.servico.Recibo;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RegistroDeIdempotencia {
    private record Registrado(OrdemDeTransferencia ordem, Recibo recibo) { }

    private final Map<String, Registrado> porChave = new ConcurrentHashMap<>();

    private static String escopo(String chave, OrdemDeTransferencia ordem) {
        return ordem.idOrigem() + ":" + chave;
    }

    public Recibo executarUmaVez(String chave, OrdemDeTransferencia ordem, Supplier<Recibo> operacao) {
        boolean[] executouAgora = {false};

        Registrado registrado = porChave.computeIfAbsent(escopo(chave, ordem), ignorada -> {
            executouAgora[0] = true;
            return new Registrado(ordem, operacao.get());
        });

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
