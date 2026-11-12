package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.seguranca.RepositorioDeCredenciais;
import br.pucminas.iceibank.controle.dto.AbrirContaRequest;
import br.pucminas.iceibank.controle.dto.ContaResposta;
import br.pucminas.iceibank.controle.dto.EventoResposta;
import br.pucminas.iceibank.controle.dto.SincronizacaoRequest;
import br.pucminas.iceibank.controle.dto.ValorRequest;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contas")
public class ContaController {
    private final ContaService servico;
    private final RepositorioDeCredenciais credenciais;
    private final int idAgencia;

    public ContaController(ContaService servico,
                           RepositorioDeCredenciais credenciais,
                           AgenciaProperties propriedades) {
        this.servico = servico;
        this.credenciais = credenciais;
        this.idAgencia = propriedades.id();
    }

    @PostMapping
    public ResponseEntity<ContaResposta> abrir(@Valid @RequestBody AbrirContaRequest pedido) {
        ContaResposta corpo = depois(servico.abrir(pedido.id(), pedido.nomeAluno(), pedido.saldoInicial()));

        if (pedido.senha() != null && !pedido.senha().isBlank()) {
            credenciais.cadastrar(pedido.id(), pedido.senha());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }

    @GetMapping("/{id}")
    public ContaResposta consultar(@PathVariable int id) {
        CarimboVetorial vetor = servico.vetorAtual();
        return ContaResposta.de(servico.consultar(id), idAgencia, vetor);
    }

    @GetMapping("/{id}/interno")
    public ContaResposta consultarInterno(@PathVariable int id) {
        CarimboVetorial vetor = servico.vetorAtual();
        return ContaResposta.de(servico.consultar(id), idAgencia, vetor);
    }

    @PostMapping("/{id}/depositar")
    public ContaResposta depositar(@PathVariable int id, @Valid @RequestBody ValorRequest pedido) {
        return depois(servico.depositar(id, pedido.valor()));
    }

    @PostMapping("/{id}/sacar")
    public ContaResposta sacar(@PathVariable int id, @Valid @RequestBody ValorRequest pedido) {
        return depois(servico.sacar(id, pedido.valor()));
    }

    @PostMapping("/{id}/sincronizar")
    public ContaResposta sincronizar(@PathVariable int id, @Valid @RequestBody SincronizacaoRequest pedido) {
        return depois(servico.sincronizar(id, pedido.tipo(), pedido.valor(),
                new CarimboVetorial(pedido.vetorConhecido())));
    }

    private ContaResposta depois(Conta conta) {
        return ContaResposta.de(conta, idAgencia, servico.vetorAtual());
    }

    @GetMapping("/{id}/historico")
    public List<EventoResposta> historico(@PathVariable int id,
                                          @RequestParam(defaultValue = "20") int limite) {
        return servico.historico(id, limite).stream().map(EventoResposta::de).toList();
    }
}
