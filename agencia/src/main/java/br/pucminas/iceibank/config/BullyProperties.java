package br.pucminas.iceibank.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bully")
public record BullyProperties(
        @Min(50) long timeoutMs,
        @Min(100) long intervaloMs,
        boolean ativo) {
}
