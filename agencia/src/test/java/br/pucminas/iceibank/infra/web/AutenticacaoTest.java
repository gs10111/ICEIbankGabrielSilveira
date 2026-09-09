package br.pucminas.iceibank.infra.web;

import br.pucminas.iceibank.infra.seguranca.ConfiguracaoDeSeguranca;
import br.pucminas.iceibank.infra.seguranca.JwtService;
import br.pucminas.iceibank.infra.seguranca.SegurancaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PARTE F — os tres cenarios que o roteiro exige (11.2):
 *   (a) requisicao sem token       -> 401
 *   (b) requisicao com token valido -> funciona
 *   (c) requisicao com token expirado -> 401
 */
@SpringBootTest
class AutenticacaoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SegurancaProperties seguranca;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(ConfiguracaoDeSeguranca.class)
                        .filtroJwt(jwtService, seguranca).getFilter())
                .build();
    }

    private String tokenValido() {
        return "Bearer " + jwtService.gerar(0, "Ana", 0);
    }

    /** Token com exp no passado, assinado com a MESMA chave: so a expiracao o invalida. */
    private String tokenExpirado() {
        Instant ontem = Instant.now().minusSeconds(86_400);
        String jwt = Jwts.builder()
                .subject("0").claim("nome", "Ana").claim("agencia", 0)
                .issuedAt(Date.from(ontem))
                .expiration(Date.from(ontem.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(seguranca.segredoJwt().getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + jwt;
    }

    @Nested
    @DisplayName("cenarios do roteiro")
    class Cenarios {

        @Test
        @DisplayName("(a) sem token: 401")
        void semTokenDevolve401() throws Exception {
            mvc().perform(get("/contas/0"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("token ausente")));
        }

        @Test
        @DisplayName("(b) com token valido: a operacao funciona")
        void comTokenValidoFunciona() throws Exception {
            mvc().perform(post("/contas").header("Authorization", tokenValido())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":600,\"nomeAluno\":\"Gil\",\"saldoInicial\":10.00,\"senha\":\"s3nh4\"}"))
                    .andExpect(status().isCreated());

            mvc().perform(get("/contas/600").header("Authorization", tokenValido()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nomeAluno").value("Gil"));
        }

        @Test
        @DisplayName("(c) token expirado: 401")
        void tokenExpiradoDevolve401() throws Exception {
            mvc().perform(get("/contas/0").header("Authorization", tokenExpirado()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("invalido ou expirado")));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("credenciais corretas devolvem token")
        void loginDevolveToken() throws Exception {
            mvc().perform(post("/contas").header("Authorization", tokenValido())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":603,\"nomeAluno\":\"Hugo\",\"saldoInicial\":10.00,\"senha\":\"minhasenha\"}"));

            mvc().perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idConta\":603,\"senha\":\"minhasenha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.expiraEmSegundos").isNumber())
                    .andExpect(jsonPath("$.agencia").value(0));
        }

        @Test
        @DisplayName("senha errada devolve 401 sem revelar se a conta existe")
        void senhaErradaDevolve401() throws Exception {
            mvc().perform(post("/contas").header("Authorization", tokenValido())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":606,\"nomeAluno\":\"Ivo\",\"saldoInicial\":10.00,\"senha\":\"certa\"}"));

            mvc().perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idConta\":606,\"senha\":\"errada\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value("credenciais invalidas"));
        }

        @Test
        @DisplayName("conta inexistente tambem devolve 401 generico (evita enumeracao de contas)")
        void contaInexistenteDevolve401Generico() throws Exception {
            mvc().perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idConta\":99999,\"senha\":\"qualquer\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value("credenciais invalidas"));
        }

        @Test
        @DisplayName("conta de outra agencia devolve 400 dizendo onde entrar")
        void contaDeOutraAgenciaDevolve400() throws Exception {
            mvc().perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idConta\":1,\"senha\":\"qualquer\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("agencia 1")));
        }

        @Test
        @DisplayName("/auth/login e publico: nao exige token")
        void loginEhPublico() throws Exception {
            mvc().perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idConta\":99999,\"senha\":\"x\"}"))
                    .andExpect(status().isUnauthorized())   // 401 de credencial, nao de filtro
                    .andExpect(jsonPath("$.erro").value("credenciais invalidas"));
        }
    }

    @Nested
    @DisplayName("chamada entre agencias")
    class EntreAgencias {

        @Test
        @DisplayName("creditar-remoto sem o token de servico e recusado")
        void semTokenDeServicoRecusa() throws Exception {
            mvc().perform(post("/contas/0/creditar-remoto").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\":10.00,\"timestampLamport\":5,\"origemAgencia\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("X-Agencia-Token")));
        }

        @Test
        @DisplayName("creditar-remoto com o token de servico e aceito, sem JWT de usuario")
        void comTokenDeServicoAceita() throws Exception {
            mvc().perform(post("/contas").header("Authorization", tokenValido())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":609,\"nomeAluno\":\"Joana\",\"saldoInicial\":10.00}"));

            mvc().perform(post("/contas/609/creditar-remoto")
                            .header("X-Agencia-Token", seguranca.tokenEntreAgencias())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\":15.00,\"timestampLamport\":42,\"origemAgencia\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saldo").value(25.00));
        }
    }
}
