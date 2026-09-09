package br.pucminas.iceibank.infra.web;

import br.pucminas.iceibank.aplicacao.conta.ContaService;
import br.pucminas.iceibank.infra.config.AgenciaProperties;
import br.pucminas.iceibank.infra.seguranca.RepositorioDeCredenciais;
import br.pucminas.iceibank.infra.web.dto.AbrirContaRequest;
import br.pucminas.iceibank.infra.web.dto.ContaResposta;
import br.pucminas.iceibank.infra.web.dto.EventoResposta;
import br.pucminas.iceibank.infra.web.dto.ValorRequest;
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

/**
 * Adapter de ENTRADA: traduz HTTP para chamadas de caso de uso e o resultado de volta.
 *
 * Nao contem regra de negocio. Se voce encontrar um `if` de negocio aqui,
 * ele esta no lugar errado — deveria estar no ContaService ou na Conta.
 * E o "C" do MVC; o "M" e o pacote dominio; a "V" sao os DTOs de resposta.
 */
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
        ContaResposta corpo = ContaResposta.de(
                servico.abrir(pedido.id(), pedido.nomeAluno(), pedido.saldoInicial()), idAgencia);

        // Orquestracao de dois adapters, nao regra de negocio: a conta e criada pelo
        // caso de uso; a credencial e assunto de seguranca e vive fora do dominio.
        // Sem senha, a conta existe mas nao pode fazer login.
        if (pedido.senha() != null && !pedido.senha().isBlank()) {
            credenciais.cadastrar(pedido.id(), pedido.senha());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }

    @GetMapping("/{id}")
    public ContaResposta consultar(@PathVariable int id) {
        return ContaResposta.de(servico.consultar(id), idAgencia);
    }

    @PostMapping("/{id}/depositar")
    public ContaResposta depositar(@PathVariable int id, @Valid @RequestBody ValorRequest pedido) {
        return ContaResposta.de(servico.depositar(id, pedido.valor()), idAgencia);
    }

    @PostMapping("/{id}/sacar")
    public ContaResposta sacar(@PathVariable int id, @Valid @RequestBody ValorRequest pedido) {
        return ContaResposta.de(servico.sacar(id, pedido.valor()), idAgencia);
    }

    /** FUNCIONALIDADE ADICIONAL 1: historico de eventos da conta. */
    @GetMapping("/{id}/historico")
    public List<EventoResposta> historico(@PathVariable int id,
                                          @RequestParam(defaultValue = "20") int limite) {
        return servico.historico(id, limite).stream().map(EventoResposta::de).toList();
    }
}
