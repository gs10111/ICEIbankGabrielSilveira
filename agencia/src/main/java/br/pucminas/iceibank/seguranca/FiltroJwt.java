package br.pucminas.iceibank.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * PARTE F — protecao das rotas, escrita a mao (sem Spring Security).
 *
 * Escolha deliberada: com ~60 linhas visiveis da para explicar exatamente o que
 * acontece em cada requisicao. O Spring Security faria o mesmo com uma cadeia de
 * filtros, providers e configuracao que eu nao conseguiria defender linha a linha
 * — e o roteiro exige poder defender o que foi entregue.
 *
 * REGRAS:
 *  - /auth/ (qualquer)     publico (senao ninguem consegue obter token)
 *  - header X-Agencia-Token valido -> chamada AGENCIA-A-AGENCIA, liberada
 *  - todo o resto           exige Authorization: Bearer <jwt> valido
 *
 * DECISAO (pergunta 11.1.5 do roteiro): a chamada interna NAO carrega JWT de usuario.
 * Um JWT identifica uma PESSOA; quem chama /creditar-remoto e um PROCESSO. Repassar o
 * token do usuario daria a uma agencia o poder de agir como ele em qualquer outra —
 * o problema classico do "confused deputy". Alem disso o token do usuario expira em
 * 15 min e a comunicacao entre agencias precisa funcionar independentemente disso.
 * Usamos um segredo de servico compartilhado (X-Agencia-Token), vindo do ambiente.
 */
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";
    public static final String ATRIBUTO_AUTENTICADO = "usuarioAutenticado";

    private final JwtService jwtService;
    private final String tokenEntreAgencias;
    private final ObjectMapper json = new ObjectMapper();

    public FiltroJwt(JwtService jwtService, String tokenEntreAgencias) {
        this.jwtService = jwtService;
        this.tokenEntreAgencias = tokenEntreAgencias;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain corrente) throws ServletException, IOException {
        String caminho = requisicao.getRequestURI();

        if (ehPublico(caminho)) {
            corrente.doFilter(requisicao, resposta);
            return;
        }

        // Chamada agencia-a-agencia: autentica com o segredo de servico, nao com JWT.
        if (tokenDeServicoConfere(requisicao)) {
            corrente.doFilter(requisicao, resposta);
            return;
        }

        if (ehChamadaEntreAgencias(caminho)) {
            recusar(resposta, "chamada entre agencias exige o header X-Agencia-Token valido");
            return;
        }

        Optional<String> token = extrairToken(requisicao);
        if (token.isEmpty()) {
            recusar(resposta, "token ausente: envie Authorization: Bearer <token>");
            return;
        }

        Optional<JwtService.Autenticado> autenticado = jwtService.validar(token.get());
        if (autenticado.isEmpty()) {
            recusar(resposta, "token invalido ou expirado");
            return;
        }

        requisicao.setAttribute(ATRIBUTO_AUTENTICADO, autenticado.get());
        corrente.doFilter(requisicao, resposta);
    }

    private boolean ehPublico(String caminho) {
        // /status e health-check: nao revela saldo nem titular.
        return caminho.startsWith("/auth/") || caminho.equals("/status") || caminho.equals("/error");
    }

    private boolean ehChamadaEntreAgencias(String caminho) {
        return caminho.endsWith("/creditar-remoto");
    }

    private boolean tokenDeServicoConfere(HttpServletRequest requisicao) {
        String recebido = requisicao.getHeader("X-Agencia-Token");
        return recebido != null && recebido.equals(tokenEntreAgencias);
    }

    private Optional<String> extrairToken(HttpServletRequest requisicao) {
        String cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);
        return cabecalho != null && cabecalho.startsWith(PREFIXO)
                ? Optional.of(cabecalho.substring(PREFIXO.length()).trim())
                : Optional.empty();
    }

    private void recusar(HttpServletResponse resposta, String mensagem) throws IOException {
        resposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        json.writeValue(resposta.getWriter(), Map.of("erro", mensagem));
    }
}
