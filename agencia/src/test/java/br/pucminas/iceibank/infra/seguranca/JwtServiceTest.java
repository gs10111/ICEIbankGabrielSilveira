package br.pucminas.iceibank.infra.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O algoritmo de assinatura precisa ser uma DECISAO nossa, nao uma consequencia
 * do tamanho do segredo. Sem informar o algoritmo, a jjwt escolhe sozinha pelo
 * tamanho da chave — trocar JWT_SEGREDO por um mais longo mudaria a criptografia
 * do sistema sem ninguem perceber, e a documentacao passaria a mentir.
 */
class JwtServiceTest {

    private static final String SEGREDO_CURTO =
            "segredo-de-teste-com-exatamente-32-b";                    // 36 bytes
    private static final String SEGREDO_LONGO =
            "iceibank-sprint1-segredo-de-desenvolvimento-trocar-em-producao"; // 62 bytes

    private static JwtService servico(String segredo) {
        return new JwtService(new SegurancaProperties(segredo, 900, "token-de-servico"));
    }

    /** Le o header do JWT: primeira das tres partes, base64url, JSON puro. */
    private static String cabecalho(String token) {
        String parte = token.split("\\.")[0];
        return new String(Base64.getUrlDecoder().decode(parte), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("assina sempre com HS256, independente do tamanho do segredo")
    void algoritmoDeAssinaturaEhExplicito() {
        assertEquals("{\"alg\":\"HS256\"}", cabecalho(servico(SEGREDO_CURTO).gerar(0, "Ana Souza", 0)));
        assertEquals("{\"alg\":\"HS256\"}", cabecalho(servico(SEGREDO_LONGO).gerar(0, "Ana Souza", 0)));
    }

    @Test
    @DisplayName("o token carrega id da conta, nome e agencia, e volta intacto na validacao")
    void tokenCarregaOsDadosDaSessao() {
        JwtService servico = servico(SEGREDO_LONGO);

        Optional<JwtService.Autenticado> autenticado = servico.validar(servico.gerar(4, "Ana Souza", 1));

        assertTrue(autenticado.isPresent());
        assertEquals(4, autenticado.get().idConta());
        assertEquals("Ana Souza", autenticado.get().nome());
        assertEquals(1, autenticado.get().agencia());
    }

    @Test
    @DisplayName("token assinado com outro segredo e recusado")
    void recusaTokenDeOutroEmissor() {
        String tokenDeFora = servico("outro-segredo-completamente-diferente-32").gerar(0, "Impostor", 0);

        assertTrue(servico(SEGREDO_LONGO).validar(tokenDeFora).isEmpty());
    }
}
