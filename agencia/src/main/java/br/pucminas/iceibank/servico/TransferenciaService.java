package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.repositorio.RegistroDeIdempotencia;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Transferencia local (mesma agencia) e entre agencias.
 *
 * Onde cada regra do relogio vetorial entra:
 *  - debito         -> eventoLocal()  (regra 1) — a origem e sempre local
 *  - credito local  -> eventoLocal()  (regra 1) — nao ha mensagem, nao ha o que sincronizar
 *  - credito remoto -> aoEnviar()     (regra 2) na origem, e aoReceber() (regra 3) no destino
 *
 * SPRINT 2: a transferencia entre agencias deixou de ser uma chamada REST sincrona e
 * virou uma MENSAGEM publicada numa exchange. A agencia de destino pode estar fora do
 * ar no momento do envio — a mensagem fica retida na fila (durable + persistente) ate
 * ela voltar. O 502 do Sprint 1 desapareceu deste caminho.
 *
 * O QUE AINDA NAO ESTA RESOLVIDO: as contas vivem em memoria. Se a agencia de destino
 * REINICIAR antes de consumir, a mensagem chega mas a conta nao existe mais — o
 * consumidor registra CREDITO_REMOTO_FALHOU e o dinheiro some do mesmo jeito. "A
 * mensagem nao se perde" nao e o mesmo que "o sistema esta correto".
 */
@Service
public class TransferenciaService {

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioVetorial relogio;
    private final RegistroDeEventos eventos;
    private final Publicador publicador;
    private final RegistroDeIdempotencia idempotencia;

    public TransferenciaService(AgenciaProperties propriedades,
                                Particionador particionador,
                                ContaRepositorio repositorio,
                                RelogioVetorial relogio,
                                RegistroDeEventos eventos,
                                Publicador publicador,
                                RegistroDeIdempotencia idempotencia) {
        this.idAgencia = propriedades.id();
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.eventos = eventos;
        this.publicador = publicador;
        this.idempotencia = idempotencia;
    }

    /**
     * FUNCIONALIDADE ADICIONAL 2 — idempotencia.
     *
     * Chave ausente = o cliente abriu mao da garantia e a transferencia segue direto.
     * Com chave, o registro executa no maximo uma vez e devolve o MESMO recibo no
     * reenvio. Falha nao e memorizada: o cliente pode tentar de novo.
     */
    public Recibo executar(OrdemDeTransferencia ordem) {
        String chave = ordem.chaveIdempotencia();
        if (chave == null || chave.isBlank()) {
            return transferir(ordem);
        }
        return idempotencia.executarUmaVez(chave, ordem, () -> transferir(ordem));
    }

    private Recibo transferir(OrdemDeTransferencia ordem) {
        validar(ordem);

        Conta origem = repositorio.buscar(ordem.idOrigem())
                .orElseThrow(() -> new ContaNaoEncontradaException(
                        "conta de origem nao encontrada nesta agencia: " + ordem.idOrigem()));

        int agenciaDestino = particionador.agenciaResponsavel(ordem.idDestino());
        return agenciaDestino == idAgencia
                ? transferirLocal(ordem, origem)
                : transferirEntreAgencias(ordem, origem, agenciaDestino);
    }

    // ------------------------------------------------------------------ local

    private Recibo transferirLocal(OrdemDeTransferencia ordem, Conta origem) {
        // Conta de destino conferida ANTES do debito: sendo tudo local, da para
        // manter a atomicidade que a versao entre agencias nao consegue ter.
        Conta destino = repositorio.buscar(ordem.idDestino())
                .orElseThrow(() -> new ContaNaoEncontradaException(
                        "conta de destino nao encontrada nesta agencia: " + ordem.idDestino()));

        CarimboVetorial carimboDebito = relogio.eventoLocal();
        origem.sacar(ordem.valor());
        eventos.registrar("TRANSFERENCIA_DEBITO", carimboDebito, detalhes(ordem));

        CarimboVetorial carimboCredito = relogio.eventoLocal();
        destino.depositar(ordem.valor());
        eventos.registrar("TRANSFERENCIA_CREDITO", carimboCredito, detalhes(ordem));

        return new Recibo("Transferencia concluida (mesma agencia).", true,
                ordem.idOrigem(), ordem.idDestino(), ordem.valor(), origem.saldo(), false);
    }

    // ---------------------------------------------------------- entre agencias

    private Recibo transferirEntreAgencias(OrdemDeTransferencia ordem, Conta origem, int agenciaDestino) {
        // Sem pre-checagem do destino, ao contrario do Sprint 1: com mensageria a
        // agencia de destino pode estar fora do ar AGORA e voltar depois. Perguntar
        // a ela antes de publicar traria de volta exatamente o acoplamento sincrono
        // que este sprint remove. Quem descobre que a conta nao existe e o CONSUMIDOR,
        // e ele registra CREDITO_REMOTO_FALHOU.
        CarimboVetorial carimboDebito = relogio.eventoLocal();
        origem.sacar(ordem.valor());                  // Conta valida saldo e valor
        eventos.registrar("TRANSFERENCIA_DEBITO", carimboDebito, detalhes(ordem));

        // Regra 2: ao ENVIAR mensagem, incrementa a propria posicao e anexa o VETOR inteiro.
        CarimboVetorial carimboEnvio = relogio.aoEnviar();

        try {
            publicador.publicarCredito(agenciaDestino, ordem.idDestino(), ordem.valor(), carimboEnvio, idAgencia);
        } catch (BrokerIndisponivelException e) {
            // O que sobrou da falha do Sprint 1, e agora so acontece se o BROKER cair:
            // o debito ja foi aplicado e o credito nao chegou nem a ser publicado.
            eventos.registrar("TRANSFERENCIA_FALHOU", relogio.eventoLocal(),
                    Map.of("idOrigem", ordem.idOrigem(), "idDestino", ordem.idDestino(),
                            "valor", ordem.valor(), "erro", String.valueOf(e.getMessage()),
                            "saldoOrigemAposDebito", origem.saldo()));
            throw e;
        }

        // 200, e nao 502: a mensagem foi publicada. Isso NAO significa que o dinheiro
        // chegou — significa que ele nao vai mais se perder no caminho.
        return new Recibo("Transferencia publicada para a agencia " + agenciaDestino + ".", false,
                ordem.idOrigem(), ordem.idDestino(), ordem.valor(), origem.saldo(), false);
    }

    // ------------------------------------------------------------ credito remoto

    /**
     * Chamado pela agencia de ORIGEM. Aplica a regra 3 do relogio vetorial.
     *
     * O relogio e ajustado ANTES de saber se a conta existe: receber a mensagem
     * ja e um evento, independente do que acontece depois com ela.
     */
    public Conta creditarRemoto(int idConta, BigDecimal valor, CarimboVetorial carimboRecebido, int agenciaOrigem) {
        CarimboVetorial carimbo = relogio.aoReceber(carimboRecebido);

        // Sem isto a recusa seria efeito colateral de a conta nao existir localmente,
        // e o cliente veria 404 ("nao encontrada") no lugar de 400 ("nao e minha").
        if (!particionador.pertenceA(idConta, idAgencia)) {
            throw new ContaNaoPertenceAgenciaException(
                    "conta " + idConta + " nao pertence a agencia " + idAgencia
                            + " (responsavel: agencia " + particionador.agenciaResponsavel(idConta) + ")");
        }

        Conta conta = repositorio.buscar(idConta)
                .orElseThrow(() -> new ContaNaoEncontradaException(
                        "conta nao encontrada nesta agencia: " + idConta));

        conta.depositar(valor);
        eventos.registrar("TRANSFERENCIA_CREDITO_REMOTO", carimbo,
                Map.of("idConta", idConta, "valor", valor,
                        "agenciaOrigem", agenciaOrigem, "novoSaldo", conta.saldo()));
        return conta;
    }

    // ------------------------------------------------------------------ apoio

    private void validar(OrdemDeTransferencia ordem) {
        if (!particionador.pertenceA(ordem.idOrigem(), idAgencia)) {
            throw new ContaNaoPertenceAgenciaException(
                    "conta de origem " + ordem.idOrigem() + " nao pertence a agencia " + idAgencia
                            + " (responsavel: agencia " + particionador.agenciaResponsavel(ordem.idOrigem()) + ")");
        }
        if (ordem.idOrigem() == ordem.idDestino()) {
            throw new ValorInvalidoException("origem e destino nao podem ser a mesma conta: " + ordem.idOrigem());
        }
    }

    private Map<String, Object> detalhes(OrdemDeTransferencia ordem) {
        return Map.of("idOrigem", ordem.idOrigem(), "idDestino", ordem.idDestino(), "valor", ordem.valor());
    }
}
