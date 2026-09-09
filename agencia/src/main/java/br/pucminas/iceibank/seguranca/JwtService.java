package br.pucminas.iceibank.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * PARTE F — geracao e validacao do JWT.
 *
 * O token carrega: subject = id da conta, claim "agencia" e "nome", alem de iat/exp.
 * A validacao e puramente matematica (conferir a assinatura HMAC) — nao consulta
 * repositorio nenhum. E isso que torna a autenticacao stateless e permite escalar
 * horizontalmente: qualquer agencia valida um token emitido por qualquer outra,
 * desde que compartilhem a chave.
 */
@Component
public class JwtService {

    /**
     * Algoritmo EXPLICITO. Sem este parametro a jjwt escolhe sozinha pelo tamanho da
     * chave (com o segredo de 62 bytes do application.yml ela sobe para HS384), e
     * trocar JWT_SEGREDO passaria a mudar a criptografia do sistema sem ninguem
     * perceber. Fixar aqui e uma decisao, nao um efeito colateral de configuracao.
     */
    private static final MacAlgorithm ALGORITMO = Jwts.SIG.HS256;

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(SegurancaProperties propriedades) {
        // HS256 exige >= 256 bits de chave; a jjwt recusa segredo curto (bom).
        this.chave = Keys.hmacShaKeyFor(propriedades.segredoJwt().getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofSeconds(propriedades.validadeEmSegundos());
    }

    public String gerar(int idConta, String nome, int agencia) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(idConta))
                .claim("nome", nome)
                .claim("agencia", agencia)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave, ALGORITMO)
                .compact();
    }

    /** Token expirado, adulterado ou mal formado devolve vazio — nunca lanca para o filtro. */
    public Optional<Autenticado> validar(String token) {
        try {
            Claims corpo = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Autenticado(
                    Integer.parseInt(corpo.getSubject()),
                    corpo.get("nome", String.class),
                    corpo.get("agencia", Integer.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long validadeEmSegundos() {
        return validade.toSeconds();
    }

    public record Autenticado(int idConta, String nome, int agencia) { }
}
