package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.controle.dto.EventoResposta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Observabilidade da agencia — alimenta a "malha de agencias" e a linha do tempo
 * do frontend (Parte E vista pela interface).
 *
 * /status e publico de proposito: e um health-check. Nao expoe saldo nem titular,
 * so o estado operacional da agencia.
 */
@RestController
public class StatusController {

    private final ContaService contaService;
    private final RegistroDeEventos consultaEventos;
    private final AgenciaProperties propriedades;

    public StatusController(ContaService contaService,
                            RegistroDeEventos consultaEventos,
                            AgenciaProperties propriedades) {
        this.contaService = contaService;
        this.consultaEventos = consultaEventos;
        this.propriedades = propriedades;
    }

    public record Status(int agencia, String nome, int totalDeAgencias, int contas, int eventos, int relogioLamport) { }

    @GetMapping("/status")
    public Status status() {
        List<EventoResposta> ultimos = consultaEventos.ultimos(1).stream().map(EventoResposta::de).toList();
        int relogio = ultimos.isEmpty() ? 0 : ultimos.get(0).timestampLamport();

        return new Status(propriedades.id(), propriedades.nome(), propriedades.total(),
                contaService.quantidadeDeContas(), consultaEventos.quantidade(), relogio);
    }

    @GetMapping("/eventos")
    public List<EventoResposta> eventos(@RequestParam(defaultValue = "50") int limite) {
        return consultaEventos.ultimos(limite).stream().map(EventoResposta::de).toList();
    }
}
