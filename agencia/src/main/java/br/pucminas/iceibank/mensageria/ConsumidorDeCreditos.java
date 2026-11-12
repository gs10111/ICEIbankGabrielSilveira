package br.pucminas.iceibank.mensageria;

import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.repositorio.RegistroDeMensagensProcessadas;
import br.pucminas.iceibank.servico.TransferenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConsumidorDeCreditos {
    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeCreditos.class);

    private final TransferenciaService transferencias;
    private final RegistroDeEventos eventos;
    private final RelogioVetorial relogio;
    private final RegistroDeMensagensProcessadas processadas;

    public ConsumidorDeCreditos(TransferenciaService transferencias,
                                RegistroDeEventos eventos,
                                RelogioVetorial relogio,
                                RegistroDeMensagensProcessadas processadas) {
        this.transferencias = transferencias;
        this.eventos = eventos;
        this.relogio = relogio;
        this.processadas = processadas;
    }

    @RabbitListener(queues = "#{filaDaAgencia.name}")
    public void aoReceberCredito(CreditoRemoto mensagem) {
        log.info("mensagem recebida {} -> creditar {} na conta {}",
                mensagem.identidade(), mensagem.valor(), mensagem.idConta());

        if (!processadas.registrarSeInedita(mensagem.identidade())) {
            eventos.registrar("CREDITO_REMOTO_DUPLICADO",
                    relogio.aoReceber(new CarimboVetorial(mensagem.vetorEnvio())),
                    Map.of("idConta", mensagem.idConta(),
                            "valor", mensagem.valor(),
                            "agenciaOrigem", mensagem.origemAgencia(),
                            "mensagem", mensagem.identidade()));
            log.warn("reentrega ignorada ({}): credito ja aplicado", mensagem.identidade());
            return;
        }

        try {
            Conta conta = transferencias.creditarRemoto(
                    mensagem.idConta(),
                    mensagem.valor(),
                    new CarimboVetorial(mensagem.vetorEnvio()),
                    mensagem.origemAgencia());
            log.info("credito aplicado: conta {} agora com {}", conta.id(), conta.saldo());
        } catch (ContaNaoEncontradaException | ContaNaoPertenceAgenciaException naoDaParaAplicar) {
            eventos.registrar("CREDITO_REMOTO_FALHOU", relogio.eventoLocal(),
                    Map.of("idConta", mensagem.idConta(),
                            "valor", mensagem.valor(),
                            "agenciaOrigem", mensagem.origemAgencia(),
                            "mensagem", mensagem.identidade(),
                            "erro", String.valueOf(naoDaParaAplicar.getMessage())));
            log.warn("credito NAO aplicado ({}): {}", mensagem.identidade(), naoDaParaAplicar.getMessage());
        }
    }
}
