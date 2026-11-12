package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.controle.dto.EventoResposta;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StatusController {
    private final ContaService contaService;
    private final RegistroDeEventos consultaEventos;
    private final AgenciaProperties propriedades;
    private final RelogioVetorial relogio;

    public StatusController(ContaService contaService,
                            RegistroDeEventos consultaEventos,
                            AgenciaProperties propriedades,
                            RelogioVetorial relogio) {
        this.contaService = contaService;
        this.consultaEventos = consultaEventos;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    public record Status(int agencia, String nome, int totalDeAgencias, int contas, int eventos,
                         java.util.List<Integer> relogioVetorial) { }

    @GetMapping("/status")
    public Status status() {
        return new Status(propriedades.id(), propriedades.nome(), propriedades.total(),
                contaService.quantidadeDeContas(), consultaEventos.quantidade(), relogio.valorAtual().valores());
    }

    @GetMapping("/eventos")
    public List<EventoResposta> eventos(@RequestParam(defaultValue = "50") int limite) {
        return consultaEventos.ultimos(limite).stream().map(EventoResposta::de).toList();
    }
}
