package br.pucminas.iceibank.mensageria;

import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.servico.TransferenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PARTE C — a entrada de mensagens desta agencia.
 *
 * Roda numa thread do container do Spring AMQP, FORA do pool do Tomcat: nao ha
 * requisicao HTTP, nao ha usuario logado, nao ha token. E por isso que a pergunta
 * 7.5.3 do roteiro existe — a resposta esta no RESPOSTAS.md.
 *
 * O que acontece quando a conta nao existe (cenario da tarefa 7.4, passos 3-5): a
 * mensagem CHEGA, o relogio avanca (receber ja e um evento), mas nao ha onde aplicar
 * o valor. Registramos CREDITO_REMOTO_FALHOU e damos ack — devolver a mensagem para a
 * fila so a faria voltar para sempre, porque a conta nao vai passar a existir. A
 * mensagem nao se perdeu; o sistema e que continua sem ser correto. E exatamente a
 * distincao da pergunta 7.5.2.
 */
@Component
public class ConsumidorDeCreditos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeCreditos.class);

    private final TransferenciaService transferencias;
    private final RegistroDeEventos eventos;
    private final RelogioVetorial relogio;

    public ConsumidorDeCreditos(TransferenciaService transferencias,
                                RegistroDeEventos eventos,
                                RelogioVetorial relogio) {
        this.transferencias = transferencias;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** A fila desta agencia, declarada em MensageriaConfig — o bean `filaDaAgencia`. */
    @RabbitListener(queues = "#{filaDaAgencia.name}")
    public void aoReceberCredito(CreditoRemoto mensagem) {
        log.info("mensagem recebida {} -> creditar {} na conta {}",
                mensagem.identidade(), mensagem.valor(), mensagem.idConta());
        try {
            Conta conta = transferencias.creditarRemoto(
                    mensagem.idConta(),
                    mensagem.valor(),
                    new CarimboVetorial(mensagem.vetorEnvio()),
                    mensagem.origemAgencia());
            log.info("credito aplicado: conta {} agora com {}", conta.id(), conta.saldo());
        } catch (ContaNaoEncontradaException | ContaNaoPertenceAgenciaException naoDaParaAplicar) {
            // A conta sumiu (restart da agencia: as contas vivem em memoria) ou nunca
            // foi desta particao. Reentregar nao resolveria nem daqui a mil tentativas.
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
