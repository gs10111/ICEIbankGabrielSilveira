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
        this.transferir = transferir;
        this.servico = servico;
        this.idAgencia = propriedades.id();
    }

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
