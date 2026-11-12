package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.seguranca.ConfiguracaoDeSeguranca;
import br.pucminas.iceibank.seguranca.JwtService;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
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

@SpringBootTest
class BullyControllerTest {
    private static final String TOKEN_DE_TESTE = "token-de-teste-entre-agencias";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtService jwtService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(contexto).addFilters(
                contexto.getBean(ConfiguracaoDeSeguranca.class)
                        .filtroJwt(jwtService, contexto.getBean(SegurancaProperties.class)).getFilter()).build();
    }

    @Test
    @DisplayName("GET /bully/lider e publico: o heartbeat e o app consultam sem credencial")
    void liderEPublico() throws Exception {
        mvc().perform(get("/bully/lider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAgencia").value(0));
    }

    @Test
    @DisplayName("POST /bully/eleicao sem X-Agencia-Token devolve 401")
    void eleicaoSemTokenRecusada() throws Exception {
        mvc().perform(post("/bully/eleicao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origemId\":1,\"vetorEnvio\":[0,1,0]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /bully/eleicao com JWT de PESSOA tambem e recusado: eleicao e conversa de processo")
    void eleicaoComJwtDeUsuarioRecusada() throws Exception {
        mvc().perform(post("/bully/eleicao").header("Authorization", "Bearer " + jwtService.gerar(0, "Ana", 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origemId\":1,\"vetorEnvio\":[0,1,0]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /bully/coordenador com token aceito muda a lider que GET /bully/lider devolve")
    void coordenadorMudaALider() throws Exception {
        mvc().perform(post("/bully/coordenador").header("X-Agencia-Token", TOKEN_DE_TESTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liderId\":2,\"vetorEnvio\":[0,0,1]}"))
                .andExpect(status().isOk());

        mvc().perform(get("/bully/lider"))
                .andExpect(jsonPath("$.liderAtual").value(2));
    }

    @Test
    @DisplayName("POST /bully/coordenador sem vetor devolve 400, nao 500")
    void coordenadorSemVetor() throws Exception {
        mvc().perform(post("/bully/coordenador").header("X-Agencia-Token", TOKEN_DE_TESTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liderId\":2}"))
                .andExpect(status().isBadRequest());
    }
}
