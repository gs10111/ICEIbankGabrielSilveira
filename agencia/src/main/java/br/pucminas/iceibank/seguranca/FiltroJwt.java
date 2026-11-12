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

        if (ehRotaInterna(caminho)) {
            if (tokenDeServicoConfere(requisicao)) {
                corrente.doFilter(requisicao, resposta);
                return;
            }
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
        return caminho.startsWith("/auth/") || caminho.equals("/status") || caminho.equals("/error")
                || caminho.equals("/bully/lider");
    }

    private boolean ehRotaInterna(String caminho) {
        return caminho.endsWith("/interno")
                || caminho.equals("/bully/eleicao") || caminho.equals("/bully/coordenador");
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
