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

/**
 * PARTE D — a linha do tempo causal.
 *
 * O roteiro (8.2) pede os DOIS lados provados: um par de eventos concorrentes, que
 * precisa aparecer, e um par causal, que precisa NAO aparecer. Um relatorio que
 * listasse tudo como concorrente passaria no primeiro teste e falharia no segundo —
 * e e exatamente por isso que os dois existem.
 *
 * O seam e `montarRelatorio(Path)`: entra uma pasta de `.jsonl`, sai o texto. Nada
 * aqui conhece o laco por dentro, entao a implementacao pode mudar de O(n^2) para
 * qualquer outra coisa sem reescrever teste nenhum.
 */
class MesclarLogsTest {

    @Test
    @DisplayName("aponta como concorrente o par de agencias diferentes em que nenhum vetor domina o outro")
    void apontaParConcorrenteEntreAgenciasDiferentes(@TempDir Path pasta) throws IOException {
        // Duas aberturas de conta simultaneas, uma em cada agencia, sem nenhuma
        // mensagem entre elas: a agencia 0 nao sabe da 1 e a 1 nao sabe da 0.
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
        // O debito na agencia 0 publicou a mensagem que virou o credito na agencia 1.
        // Pela regra 3 o destino absorveu o vetor da origem: [1,0,0] esta contido em
        // [1,1,0] posicao a posicao, entao a relacao e ANTES, nunca CONCORRENTES.
        gravar(pasta, "agencia-0", "TRANSFERENCIA_DEBITO", "[1,0,0]", "2026-09-28T12:00:00Z");
        gravar(pasta, "agencia-1", "TRANSFERENCIA_CREDITO_REMOTO", "[1,1,0]", "2026-09-28T12:00:02Z");

        String relatorio = MesclarLogs.montarRelatorio(pasta);

        assertThat(relatorio).doesNotContain("  x  ");
        assertThat(relatorio).contains(
                "(nenhum par concorrente nesta execucao - gere operacoes independentes"
                        + " em agencias diferentes e rode de novo)");
    }

    // ------------------------------------------------------------------ apoio

    private static void gravar(Path pasta, String agencia, String tipo, String vetor, String hora)
            throws IOException {
        String linha = """
                {"agencia":"%s","tipo":"%s","timestampVetorial":%s,"horaParede":"%s","detalhes":{}}
                """.formatted(agencia, tipo, vetor, hora);
        Files.writeString(pasta.resolve("eventos-" + agencia + ".jsonl"), linha,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
