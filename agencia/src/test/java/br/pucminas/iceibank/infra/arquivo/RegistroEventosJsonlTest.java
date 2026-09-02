package br.pucminas.iceibank.infra.arquivo;

import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.relogio.CarimboLamport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroEventosJsonlTest {

    @TempDir
    Path pastaTemporaria;

    @Test
    @DisplayName("grava uma linha JSON por evento, no formato do roteiro")
    void gravaUmaLinhaJsonPorEvento() throws IOException {
        RegistroEventosJsonl registro = new RegistroEventosJsonl("agencia-0", pastaTemporaria);

        Evento evento = registro.registrar("CRIAR_CONTA", new CarimboLamport(1),
                Map.of("id", 0, "nomeAluno", "Ana"));

        List<String> linhas = Files.readAllLines(registro.caminhoArquivo());
        assertThat(linhas).hasSize(1);

        JsonNode lido = new ObjectMapper().readTree(linhas.get(0));
        assertThat(lido.get("agencia").asText()).isEqualTo("agencia-0");
        assertThat(lido.get("tipo").asText()).isEqualTo("CRIAR_CONTA");
        assertThat(lido.get("timestampLamport").asInt()).isEqualTo(1);
        assertThat(lido.get("horaParede").asText()).isNotBlank();
        assertThat(lido.get("detalhes").get("nomeAluno").asText()).isEqualTo("Ana");

        assertThat(evento.tipo()).isEqualTo("CRIAR_CONTA");
    }

    @Test
    @DisplayName("eventos sao acrescentados ao arquivo, nunca sobrescritos")
    void acrescentaSemSobrescrever() throws IOException {
        RegistroEventosJsonl registro = new RegistroEventosJsonl("agencia-0", pastaTemporaria);

        registro.registrar("CRIAR_CONTA", new CarimboLamport(1), Map.of("id", 0));
        registro.registrar("DEPOSITO", new CarimboLamport(2), Map.of("id", 0, "valor", 25));

        assertThat(Files.readAllLines(registro.caminhoArquivo())).hasSize(2);
    }

    @Test
    @DisplayName("o nome do arquivo identifica a agencia")
    void nomeDoArquivoIdentificaAgencia() {
        RegistroEventosJsonl registro = new RegistroEventosJsonl("agencia-2", pastaTemporaria);

        assertThat(registro.caminhoArquivo().getFileName().toString()).isEqualTo("eventos-agencia-2.jsonl");
    }
}
