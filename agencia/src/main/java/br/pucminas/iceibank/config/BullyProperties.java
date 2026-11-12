package br.pucminas.iceibank.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros da eleicao de lider (Sprint 3), vindos do ambiente como o resto da agencia.
 *
 *  - timeoutMs   quanto esperar a resposta de outra agencia antes de considera-la fora do ar.
 *                E o MESMO numero para "nao respondeu a eleicao" e "a lider caiu" — ver a
 *                pergunta 6.4.3 do roteiro sobre o que isso custa numa rede lenta.
 *  - intervaloMs de quanto em quanto tempo o heartbeat confere se a lider continua viva.
 *  - ativo       false so nos testes: senao cada @SpringBootTest dispararia eleicoes de
 *                verdade contra localhost:4016-4018 e um heartbeat em segundo plano.
 */
@Validated
@ConfigurationProperties(prefix = "bully")
public record BullyProperties(
        @Min(50) long timeoutMs,
        @Min(100) long intervaloMs,
        boolean ativo) {
}
