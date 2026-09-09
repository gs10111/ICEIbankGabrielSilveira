package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.servico.RegistroIdempotencia;

/**
 * FUNCIONALIDADE ADICIONAL 2 — idempotencia de transferencias, como DECORATOR.
 *
 * Por que decorator e nao um `if` dentro de TransferenciaService:
 *  - TransferenciaService nunca foi editado para ganhar idempotencia. Comportamento
 *    novo sem modificar codigo existente = Open/Closed literal.
 *  - Cada classe tem UM motivo para mudar: uma sabe transferir, a outra sabe deduplicar.
 *  - Aplicar a mesma politica a saques amanha e escrever outro decorator, nao duplicar codigo.
 *
 * Chave ausente = o cliente abriu mao da garantia; segue direto.
 */
public class TransferenciaIdempotente implements Transferir {

    private final Transferir interno;
    private final RegistroIdempotencia registro;

    public TransferenciaIdempotente(Transferir interno, RegistroIdempotencia registro) {
        this.interno = interno;
        this.registro = registro;
    }

    @Override
    public Recibo executar(OrdemDeTransferencia ordem) {
        String chave = ordem.chaveIdempotencia();
        if (chave == null || chave.isBlank()) {
            return interno.executar(ordem);
        }
        return registro.executarUmaVez(chave, ordem, () -> interno.executar(ordem));
    }
}
