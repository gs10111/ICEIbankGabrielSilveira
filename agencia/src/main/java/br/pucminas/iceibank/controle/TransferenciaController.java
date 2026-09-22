package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.OrdemDeTransferencia;
import br.pucminas.iceibank.servico.Recibo;
import br.pucminas.iceibank.servico.TransferenciaService;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.controle.dto.ContaResposta;
import br.pucminas.iceibank.controle.dto.TransferenciaRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferenciaController {

    private final TransferenciaService transferir;
    private final TransferenciaService servico;
    private final int idAgencia;

    public TransferenciaController(TransferenciaService transferir,
                                   TransferenciaService servico,
                                   AgenciaProperties propriedades) {
        this.transferir = transferir;       // ja vem DECORADO com idempotencia
        this.servico = servico;             // usado so no credito remoto
        this.idAgencia = propriedades.id();
    }

    /**
     * A chave de idempotencia vem no header `Idempotency-Key` (convencao de mercado)
     * ou no corpo. Quem gera e o CLIENTE — se o servidor gerasse, cada reenvio teria
     * chave nova e a deduplicacao nao serviria para nada.
     */
    @PostMapping("/transferencias")
    public Recibo transferir(@RequestHeader(value = "Idempotency-Key", required = false) String chaveDoHeader,
                             @Valid @RequestBody TransferenciaRequest pedido) {
        String chave = chaveDoHeader != null && !chaveDoHeader.isBlank()
                ? chaveDoHeader
                : pedido.chaveIdempotencia();

        return transferir.executar(
                new OrdemDeTransferencia(chave, pedido.idOrigem(), pedido.idDestino(), pedido.valor()));
    }
}
