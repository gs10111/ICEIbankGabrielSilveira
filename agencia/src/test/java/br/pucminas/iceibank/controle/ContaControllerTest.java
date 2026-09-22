package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.seguranca.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste da camada web: valida o que o controller acrescenta ao service —
 * roteamento, codigos HTTP, formato JSON e traducao de excecao em status.
 *
 * A REGRA ja foi testada em ContaServiceTest sem subir Spring; aqui nao repetimos regra.
 */
@SpringBootTest
class ContaControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtService jwtService;

    /** addFilters(): sem isso o MockMvc pula o FiltroJwt e o teste nao provaria nada. */
    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(contexto).addFilters(
                contexto.getBean(br.pucminas.iceibank.seguranca.ConfiguracaoDeSeguranca.class)
                        .filtroJwt(jwtService, contexto.getBean(
                                br.pucminas.iceibank.seguranca.SegurancaProperties.class)).getFilter()).build();
    }

    private String autorizacao() {
        return "Bearer " + jwtService.gerar(0, "Ana", 0);
    }

    @Test
    @DisplayName("POST /contas cria a conta e devolve 201")
    void criaConta() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":300,\"nomeAluno\":\"Ana\",\"saldoInicial\":100.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.saldo").value(100.00))
                .andExpect(jsonPath("$.agencia").value(0));
    }

    @Test
    @DisplayName("POST /contas de conta que nao e desta agencia devolve 400")
    void recusaContaDeOutraAgencia() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":301,\"nomeAluno\":\"Bia\",\"saldoInicial\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("nao pertence")));
    }

    @Test
    @DisplayName("GET /contas/{id} inexistente devolve 404")
    void consultaInexistenteDevolve404() throws Exception {
        mvc().perform(get("/contas/999999").header("Authorization", autorizacao()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deposito e saque alteram o saldo pela API")
    void depositoESaquePelaApi() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":303,\"nomeAluno\":\"Caio\",\"saldoInicial\":50.00}"));

        mvc().perform(post("/contas/303/depositar").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(75.00));

        mvc().perform(post("/contas/303/sacar").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":30.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(45.00));
    }

    @Test
    @DisplayName("saque acima do saldo devolve 400 com mensagem de dominio")
    void saqueAcimaDoSaldoDevolve400() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":306,\"nomeAluno\":\"Duda\",\"saldoInicial\":10.00}"));

        mvc().perform(post("/contas/306/sacar").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":999.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("saldo insuficiente")));
    }

    @Test
    @DisplayName("valor negativo e barrado pela validacao do DTO, antes do dominio")
    void valorNegativoDevolve400() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":309,\"nomeAluno\":\"Edu\",\"saldoInicial\":10.00}"));

        mvc().perform(post("/contas/309/depositar").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":-5.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("positivo")));
    }

    @Test
    @DisplayName("GET /contas/{id}/historico devolve os eventos carimbados da conta")
    void historicoDaConta() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":312,\"nomeAluno\":\"Fabi\",\"saldoInicial\":10.00}"));
        mvc().perform(post("/contas/312/depositar").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":5.00}"));

        mvc().perform(get("/contas/312/historico").header("Authorization", autorizacao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$[1].tipo").value("CRIAR_CONTA"))
                .andExpect(jsonPath("$[0].timestampVetorial").isArray());
    }

    @Test
    @DisplayName("limite negativo no historico e erro do cliente (400), nao do servidor")
    void limiteNegativoNoHistoricoDevolve400() throws Exception {
        mvc().perform(post("/contas").header("Authorization", autorizacao()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":318,\"nomeAluno\":\"Rui\",\"saldoInicial\":10.00}"));

        mvc().perform(get("/contas/318/historico?limite=-1").header("Authorization", autorizacao()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("limite")));
    }

    @Test
    @DisplayName("id de conta negativo e erro do cliente (400), nao do servidor")
    void idNegativoDevolve400() throws Exception {
        mvc().perform(get("/contas/-1").header("Authorization", autorizacao()))
                .andExpect(status().isBadRequest());
    }
}
