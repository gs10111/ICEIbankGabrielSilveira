package br.pucminas.iceibank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuracao da agencia, tipada e validada no boot.
 *
 * Todos os valores tem default de desenvolvimento no application.yml e podem ser
 * sobrescritos por variavel de ambiente (AGENCIA_ID, AGENCIA_TOTAL, AGENCIA_URLS,
 * SERVER_PORT) — 12-Factor III. O mesmo .jar roda como as 3 agencias sem recompilar,
 * e no Sprint 4 o docker-compose so precisa passar `environment:`.
 *
 * Se o YAML estiver errado a aplicacao NAO sobe: melhor falhar no boot do que
 * virar null misterioso na terceira requisicao.
 */
@Validated
@ConfigurationProperties(prefix = "agencia")
public record AgenciaProperties(
        @Min(0) int id,
        @Min(1) int total,
        @NotEmpty List<String> urls,
        String pastaDados) {

    public AgenciaProperties {
        if (urls != null && id >= urls.size()) {
            throw new IllegalArgumentException(
                    "agencia.id=" + id + " nao tem URL correspondente em agencia.urls (" + urls.size() + " itens)");
        }
        if (pastaDados == null || pastaDados.isBlank()) {
            pastaDados = "data";
        }
    }

    public String nome() {
        return "agencia-" + id;
    }

    public String urlDa(int idAgencia) {
        return urls.get(idAgencia);
    }
}
