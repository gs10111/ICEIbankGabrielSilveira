package br.pucminas.iceibank.ferramentas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class MesclarLogsTest {
    @Test
    @DisplayName("aponta como concorrente o par de agencias diferentes em que nenhum vetor domina o outro")
    void apontaParConcorrenteEntreAgenciasDiferentes(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-0", "ABERTURA_DE_CONTA", "[1,0,0]", "2026-09-28T12:00:00Z");
        gravar(pasta, "agencia-1", "ABERTURA_DE_CONTA", "[0,1,0]", "2026-09-28T12:00:01Z");

        String relatorio = MesclarLogs.montarRelatorio(pasta);

        assertThat(relatorio).contains("=== Pares de eventos CONCORRENTES (agencias diferentes) ===");
        assertThat(relatorio).contains(
                "[agencia-0] ABERTURA_DE_CONTA [1, 0, 0]  x  [agencia-1] ABERTURA_DE_CONTA [0, 1, 0]");
    }

    @Test
    @DisplayName("transferencia entre agencias NAO aparece como concorrente: ha cadeia causal")
    void parCausalNaoApareceComoConcorrente(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-0", "TRANSFERENCIA_DEBITO", "[1,0,0]", "2026-09-28T12:00:00Z");
        gravar(pasta, "agencia-1", "TRANSFERENCIA_CREDITO_REMOTO", "[1,1,0]", "2026-09-28T12:00:02Z");

        String relatorio = MesclarLogs.montarRelatorio(pasta);

        assertThat(relatorio).doesNotContain("  x  ");
        assertThat(relatorio).contains(
                "(nenhum par concorrente nesta execucao - gere operacoes independentes"
                        + " em agencias diferentes e rode de novo)");
    }

    @Test
    @DisplayName("o resumo conta os pares concorrentes: o numero cabe no print, a lista nao")
    void resumoTrazAContagemDeParesConcorrentes(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-0", "CRIAR_CONTA", "[1,0,0]", "2026-09-28T12:00:00Z");
        gravar(pasta, "agencia-1", "CRIAR_CONTA", "[0,1,0]", "2026-09-28T12:00:01Z");
        gravar(pasta, "agencia-2", "CRIAR_CONTA", "[0,0,1]", "2026-09-28T12:00:02Z");

        String relatorio = MesclarLogs.montarRelatorio(pasta);

        assertThat(relatorio).contains("pares concorrentes: 3");
    }

    private static void gravar(Path pasta, String agencia, String tipo, String vetor, String hora)
            throws IOException {
        String linha = """
                {"agencia":"%s","tipo":"%s","timestampVetorial":%s,"horaParede":"%s","detalhes":{}}
                """.formatted(agencia, tipo, vetor, hora);
        Files.writeString(pasta.resolve("eventos-" + agencia + ".jsonl"), linha,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
