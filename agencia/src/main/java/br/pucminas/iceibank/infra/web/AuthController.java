package br.pucminas.iceibank.infra.web;

import br.pucminas.iceibank.aplicacao.conta.ContaService;
import br.pucminas.iceibank.dominio.conta.Conta;
import br.pucminas.iceibank.dominio.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.dominio.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.infra.config.AgenciaProperties;
import br.pucminas.iceibank.infra.seguranca.JwtService;
import br.pucminas.iceibank.infra.seguranca.RepositorioDeCredenciais;
import br.pucminas.iceibank.infra.web.dto.ErroResposta;
import br.pucminas.iceibank.infra.web.dto.LoginRequest;
import br.pucminas.iceibank.infra.web.dto.LoginResposta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ContaService contaService;
    private final RepositorioDeCredenciais credenciais;
    private final JwtService jwtService;
    private final int idAgencia;

    public AuthController(ContaService contaService,
                          RepositorioDeCredenciais credenciais,
                          JwtService jwtService,
                          AgenciaProperties propriedades) {
        this.contaService = contaService;
        this.credenciais = credenciais;
        this.jwtService = jwtService;
        this.idAgencia = propriedades.id();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest pedido) {
        Conta conta;
        try {
            conta = contaService.consultar(pedido.idConta());
        } catch (ContaNaoPertenceAgenciaException e) {
            // 400 e nao 401: o problema nao e credencial, e porta de entrada errada.
            // A mensagem diz qual agencia procurar.
            return ResponseEntity.badRequest().body(new ErroResposta(e.getMessage()));
        } catch (ContaNaoEncontradaException e) {
            // 401 generico de proposito: dizer "conta nao existe" permitiria a um
            // atacante enumerar contas validas antes de tentar senhas.
            return naoAutorizado();
        }

        if (!credenciais.senhaConfere(pedido.idConta(), pedido.senha())) {
            return naoAutorizado();
        }

        String token = jwtService.gerar(conta.id(), conta.nome(), idAgencia);
        return ResponseEntity.ok(new LoginResposta(
                token, jwtService.validadeEmSegundos(), conta.id(), conta.nome(), idAgencia));
    }

    private ResponseEntity<ErroResposta> naoAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResposta("credenciais invalidas"));
    }
}
