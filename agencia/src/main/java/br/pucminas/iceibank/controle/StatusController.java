package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.controle.dto.EventoResposta;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
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
    private final RelogioLamport relogio;

    public StatusController(ContaService contaService,
                            RegistroDeEventos consultaEventos,
                            AgenciaProperties propriedades,
                            RelogioLamport relogio) {
        this.contaService = contaService;
        this.consultaEventos = consultaEventos;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    public record Status(int agencia, String nome, int totalDeAgencias, int contas, int eventos, int relogioLamport) { }

    @GetMapping("/status")
    public Status status() {
        // Pergunta ao proprio relogio. Antes deduzia do ultimo evento do arquivo, o
        // que so funcionava enquanto o ultimo carimbo fosse o maior — e depois de um
        // restart ele nao e.
        return new Status(propriedades.id(), propriedades.nome(), propriedades.total(),
                contaService.quantidadeDeContas(), consultaEventos.quantidade(), relogio.valorAtual());
    }

    @GetMapping("/eventos")
    public List<EventoResposta> eventos(@RequestParam(defaultValue = "50") int limite) {
        return consultaEventos.ultimos(limite).stream().map(EventoResposta::de).toList();
    }
}
