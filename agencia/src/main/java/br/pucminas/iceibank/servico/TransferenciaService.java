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

    private Recibo transferirLocal(OrdemDeTransferencia ordem, Conta origem) {
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

    private Recibo transferirEntreAgencias(OrdemDeTransferencia ordem, Conta origem, int agenciaDestino) {
        CarimboVetorial carimboDebito = relogio.eventoLocal();
        origem.sacar(ordem.valor());
        eventos.registrar("TRANSFERENCIA_DEBITO", carimboDebito, detalhes(ordem));

        CarimboVetorial carimboEnvio = relogio.aoEnviar();

        try {
            publicador.publicarCredito(agenciaDestino, ordem.idDestino(), ordem.valor(), carimboEnvio, idAgencia);
        } catch (BrokerIndisponivelException e) {
            eventos.registrar("TRANSFERENCIA_FALHOU", relogio.eventoLocal(),
                    Map.of("idOrigem", ordem.idOrigem(), "idDestino", ordem.idDestino(),
                            "valor", ordem.valor(), "erro", String.valueOf(e.getMessage()),
                            "saldoOrigemAposDebito", origem.saldo()));
            throw e;
        }

        return new Recibo("Transferencia publicada para a agencia " + agenciaDestino + ".", false,
                ordem.idOrigem(), ordem.idDestino(), ordem.valor(), origem.saldo(), false);
    }

    public Conta creditarRemoto(int idConta, BigDecimal valor, CarimboVetorial carimboRecebido, int agenciaOrigem) {
        CarimboVetorial carimbo = relogio.aoReceber(carimboRecebido);

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
