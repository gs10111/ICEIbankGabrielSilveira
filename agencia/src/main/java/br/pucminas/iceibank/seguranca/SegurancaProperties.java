package br.pucminas.iceibank.seguranca;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "seguranca")
public record SegurancaProperties(
        @Size(min = 32, message = "segredo do JWT precisa de ao menos 32 caracteres (256 bits para HS256)")
        String segredoJwt,
        @Min(10) long validadeEmSegundos,
        String tokenEntreAgencias) {
}
