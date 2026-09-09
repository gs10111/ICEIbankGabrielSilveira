package br.pucminas.iceibank.infra.web;

import br.pucminas.iceibank.aplicacao.conta.ExtratoConsolidadoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FUNCIONALIDADE ADICIONAL 3.
 *
 *   GET /extrato-consolidado?contas=0,1,3
 *   GET /extrato-consolidado?titular=Ana        (usa as contas do titular NESTA agencia)
 */
@RestController
public class ExtratoController {

    private final ExtratoConsolidadoService servico;

    public ExtratoController(ExtratoConsolidadoService servico) {
        this.servico = servico;
    }

    @GetMapping("/extrato-consolidado")
    public ExtratoConsolidadoService.Extrato consolidar(
            @RequestParam(required = false) List<Integer> contas,
            @RequestParam(required = false) String titular) {

        List<Integer> ids = (contas != null && !contas.isEmpty())
                ? contas
                : servico.idsDoTitularLocal(titular == null ? "" : titular);

        return servico.consolidar(ids);
    }
}
