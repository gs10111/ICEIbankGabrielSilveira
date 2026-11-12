package br.pucminas.iceibank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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
