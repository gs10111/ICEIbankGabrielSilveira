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
        ContaResposta corpo = depois(servico.abrir(pedido.id(), pedido.nomeAluno(), pedido.saldoInicial()));

        // Orquestracao de dois adapters, nao regra de negocio: a conta e criada pelo
        // caso de uso; a credencial e assunto de seguranca e vive fora do dominio.
        // Sem senha, a conta existe mas nao pode fazer login.
        if (pedido.senha() != null && !pedido.senha().isBlank()) {
            credenciais.cadastrar(pedido.id(), pedido.senha());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }

    /**
     * Le o vetor ANTES da conta. Se um deposito entrar no meio, o app fica com um vetor
     * mais velho que o saldo — e a pior consequencia e um 409 a mais. Na ordem inversa o
     * vetor ja incluiria um deposito que o saldo lido nao mostra, e a sincronizacao seria
     * aceita sobre um saldo que o app nunca viu.
     */
    @GetMapping("/{id}")
    public ContaResposta consultar(@PathVariable int id) {
        CarimboVetorial vetor = servico.vetorAtual();
        return ContaResposta.de(servico.consultar(id), idAgencia, vetor);
    }

    /**
     * Leitura ENTRE AGENCIAS, usada pelo extrato consolidado. Separada de GET /contas/{id}
     * porque o segredo de servico so pode abrir rota interna — se as duas fossem a mesma
     * rota, o segredo abriria a consulta de saldo de qualquer pessoa.
     */
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

    /**
     * SPRINT 3, Parte E — aplica uma operacao feita offline pelo app, se a agencia nao
     * mudou desde a leitura dele. Senao, 409 com o estado atual (ManipuladorDeErros).
     */
    @PostMapping("/{id}/sincronizar")
    public ContaResposta sincronizar(@PathVariable int id, @Valid @RequestBody SincronizacaoRequest pedido) {
        return depois(servico.sincronizar(id, pedido.tipo(), pedido.valor(),
                new CarimboVetorial(pedido.vetorConhecido())));
    }

    /** Resposta de uma escrita: o vetor e lido depois, e ja inclui a operacao recem-aplicada. */
    private ContaResposta depois(Conta conta) {
        return ContaResposta.de(conta, idAgencia, servico.vetorAtual());
    }

    /** FUNCIONALIDADE ADICIONAL 1: historico de eventos da conta. */
    @GetMapping("/{id}/historico")
    public List<EventoResposta> historico(@PathVariable int id,
                                          @RequestParam(defaultValue = "20") int limite) {
        return servico.historico(id, limite).stream().map(EventoResposta::de).toList();
    }
}
