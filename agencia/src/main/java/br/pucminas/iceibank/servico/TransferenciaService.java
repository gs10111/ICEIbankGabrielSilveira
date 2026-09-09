package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.repositorio.RegistroDeIdempotencia;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Transferencia local (mesma agencia) e entre agencias.
 *
 * Onde cada regra de Lamport entra:
 *  - debito         -> eventoLocal()  (regra 1) — a origem e sempre local
 *  - credito local  -> eventoLocal()  (regra 1) — nao ha mensagem, nao ha o que sincronizar
 *  - credito remoto -> aoEnviar()     (regra 2) na origem, e aoReceber() (regra 3) no destino
 *
 * LIMITACAO CONHECIDA (Parte D do roteiro): se a agencia de destino cair depois do
 * debito, o dinheiro "some". Nao revertemos de proposito — e o problema que o
 * Sprint 4 resolve com 2PC ou Saga. Aqui so registramos TRANSFERENCIA_FALHOU.
 */
@Service
public class TransferenciaService {

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioLamport relogio;
    private final RegistroDeEventos eventos;
    private final AgenciaRemota agenciaRemota;
    private final RegistroDeIdempotencia idempotencia;

    public TransferenciaService(AgenciaProperties propriedades,
                                Particionador particionador,
                                ContaRepositorio repositorio,
                                RelogioLamport relogio,
                                RegistroDeEventos eventos,
                                AgenciaRemota agenciaRemota,
                                RegistroDeIdempotencia idempotencia) {
        this.idAgencia = propriedades.id();
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.eventos = eventos;
        this.agenciaRemota = agenciaRemota;
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

        Carimbo carimboDebito = relogio.eventoLocal();
        origem.sacar(ordem.valor());
        repositorio.salvar(origem);
        eventos.registrar("TRANSFERENCIA_DEBITO", carimboDebito, detalhes(ordem));

        Carimbo carimboCredito = relogio.eventoLocal();
        destino.depositar(ordem.valor());
        repositorio.salvar(destino);
        eventos.registrar("TRANSFERENCIA_CREDITO", carimboCredito, detalhes(ordem));

        return new Recibo("Transferencia concluida (mesma agencia).", true,
                ordem.idOrigem(), ordem.idDestino(), ordem.valor(), origem.saldo(), false);
    }

    // ---------------------------------------------------------- entre agencias

    private Recibo transferirEntreAgencias(OrdemDeTransferencia ordem, Conta origem, int agenciaDestino) {
        Carimbo carimboDebito = relogio.eventoLocal();
        origem.sacar(ordem.valor());                  // Conta valida saldo e valor
        repositorio.salvar(origem);
        eventos.registrar("TRANSFERENCIA_DEBITO", carimboDebito, detalhes(ordem));

        // Regra 2 de Lamport: ao ENVIAR mensagem, incrementa e anexa o carimbo.
        Carimbo carimboEnvio = relogio.aoEnviar();

        try {
            agenciaRemota.creditar(agenciaDestino, ordem.idDestino(), ordem.valor(), carimboEnvio, idAgencia);
        } catch (AgenciaRemotaIndisponivelException e) {
            // LIMITACAO CONHECIDA: o debito acima NAO e revertido. Sprint 4 (2PC/Saga).
            eventos.registrar("TRANSFERENCIA_FALHOU", relogio.eventoLocal(),
                    Map.of("idOrigem", ordem.idOrigem(), "idDestino", ordem.idDestino(),
                            "valor", ordem.valor(), "erro", String.valueOf(e.getMessage()),
                            "saldoOrigemAposDebito", origem.saldo()));
            throw e;
        }

        return new Recibo("Transferencia concluida (entre agencias).", false,
                ordem.idOrigem(), ordem.idDestino(), ordem.valor(), origem.saldo(), false);
    }

    // ------------------------------------------------------------ credito remoto

    /**
     * Chamado pela agencia de ORIGEM. Aplica a regra 3 de Lamport.
     *
     * O relogio e ajustado ANTES de saber se a conta existe: receber a mensagem
     * ja e um evento, independente do que acontece depois com ela.
     */
    public Conta creditarRemoto(int idConta, BigDecimal valor, Carimbo carimboRecebido, int agenciaOrigem) {
        Carimbo carimbo = relogio.aoReceber(carimboRecebido);

        Conta conta = repositorio.buscar(idConta)
                .orElseThrow(() -> new ContaNaoEncontradaException(
                        "conta nao encontrada nesta agencia: " + idConta));

        conta.depositar(valor);
        repositorio.salvar(conta);
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
