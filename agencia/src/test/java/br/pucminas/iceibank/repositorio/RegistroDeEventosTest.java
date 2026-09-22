package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
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

class RegistroDeEventosTest {

    @TempDir
    Path pastaTemporaria;

    @Test
    @DisplayName("grava uma linha JSON por evento, no formato do roteiro")
    void gravaUmaLinhaJsonPorEvento() throws IOException {
        RegistroDeEventos registro = new RegistroDeEventos(configuracao(0, pastaTemporaria));

        Evento evento = registro.registrar("CRIAR_CONTA", vetor(1, 0, 0),
                Map.of("id", 0, "nomeAluno", "Ana"));

        List<String> linhas = Files.readAllLines(registro.caminhoArquivo());
        assertThat(linhas).hasSize(1);

        JsonNode lido = new ObjectMapper().readTree(linhas.get(0));
        assertThat(lido.get("agencia").asText()).isEqualTo("agencia-0");
        assertThat(lido.get("tipo").asText()).isEqualTo("CRIAR_CONTA");
        assertThat(lido.get("timestampVetorial").isArray()).as("o carimbo virou ARRAY no Sprint 2").isTrue();
        assertThat(lido.get("timestampVetorial").toString()).isEqualTo("[1,0,0]");
        assertThat(lido.get("horaParede").asText()).isNotBlank();
        assertThat(lido.get("detalhes").get("nomeAluno").asText()).isEqualTo("Ana");

        assertThat(evento.tipo()).isEqualTo("CRIAR_CONTA");
    }

    @Test
    @DisplayName("eventos sao acrescentados ao arquivo, nunca sobrescritos")
    void acrescentaSemSobrescrever() throws IOException {
        RegistroDeEventos registro = new RegistroDeEventos(configuracao(0, pastaTemporaria));

        registro.registrar("CRIAR_CONTA", vetor(1, 0, 0), Map.of("id", 0));
        registro.registrar("DEPOSITO", vetor(2, 0, 0), Map.of("id", 0, "valor", 25));

        assertThat(Files.readAllLines(registro.caminhoArquivo())).hasSize(2);
    }

    @Test
    @DisplayName("o nome do arquivo identifica a agencia")
    void nomeDoArquivoIdentificaAgencia() {
        RegistroDeEventos registro = new RegistroDeEventos(configuracao(2, pastaTemporaria));

        assertThat(registro.caminhoArquivo().getFileName().toString()).isEqualTo("eventos-agencia-2.jsonl");
    }

    /** A configuracao de uma agencia apontando para a pasta temporaria do teste. */
    private static AgenciaProperties configuracao(int id, Path pasta) {
        return new AgenciaProperties(id, 3,
                List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
                pasta.toString());
    }

    @Test
    @DisplayName("vetorRestaurado devolve o maximo POSICAO A POSICAO, nao o ultimo vetor")
    void vetorRestauradoEhOMaximoPosicaoAPosicao() {
        RegistroDeEventos registro = new RegistroDeEventos(configuracao(0, pastaTemporaria));

        registro.registrar("CRIAR_CONTA", vetor(1, 0, 0), Map.of("id", 0));
        registro.registrar("DEPOSITO", vetor(5, 2, 0), Map.of("id", 0));
        registro.registrar("SAQUE", vetor(3, 7, 1), Map.of("id", 0));

        // o ULTIMO e [3,7,1], mas o maximo posicao a posicao e [5,7,1]
        assertThat(registro.vetorRestaurado(3)).isEqualTo(vetor(5, 7, 1));
    }

    @Test
    @DisplayName("sem arquivo de eventos, o vetor restaurado e de zeros")
    void vetorRestauradoSemArquivo() {
        RegistroDeEventos registro = new RegistroDeEventos(configuracao(1, pastaTemporaria));

        assertThat(registro.vetorRestaurado(3)).isEqualTo(vetor(0, 0, 0));
    }


    /** Atalho: vetor(1, 0, 0) em vez de new CarimboVetorial(List.of(1, 0, 0)). */
    private static CarimboVetorial vetor(int... valores) {
        return new CarimboVetorial(java.util.Arrays.stream(valores).boxed().toList());
    }
}
