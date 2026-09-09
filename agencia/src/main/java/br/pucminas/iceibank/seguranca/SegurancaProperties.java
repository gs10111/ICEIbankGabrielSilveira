package br.pucminas.iceibank.seguranca;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * O segredo vem do ambiente (JWT_SEGREDO). O default do application.yml serve
 * apenas para desenvolvimento — em producao, segredo em codigo e vazamento
 * garantido no momento em que o repositorio virar publico.
 *
 * As 3 agencias compartilham o mesmo segredo para que um token emitido por uma
 * seja aceito pelas outras.
 */
@Validated
@ConfigurationProperties(prefix = "seguranca")
public record SegurancaProperties(
        @Size(min = 32, message = "segredo do JWT precisa de ao menos 32 caracteres (256 bits para HS256)")
        String segredoJwt,
        @Min(10) long validadeEmSegundos,
        String tokenEntreAgencias) {
}
