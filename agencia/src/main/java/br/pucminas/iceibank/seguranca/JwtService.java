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

@Component
public class JwtService {
    private static final MacAlgorithm ALGORITMO = Jwts.SIG.HS256;

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(SegurancaProperties propriedades) {
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
