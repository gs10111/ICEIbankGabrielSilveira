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

class HistoricoLiderancaTest {
    @Test
    @DisplayName("reconstroi lider inicial -> queda -> nova lider, em ordem de hora de parede")
    void reconstroiQuedaENovaLider(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-2", "TORNOU_LIDER", "{\"idAgencia\":2}", "2026-11-12T12:00:01Z");
        gravar(pasta, "agencia-1", "LIDER_NAO_RESPONDEU", "{\"liderAtual\":2,\"agencia\":1}", "2026-11-12T12:05:00Z");
        gravar(pasta, "agencia-1", "TORNOU_LIDER", "{\"idAgencia\":1}", "2026-11-12T12:05:01Z");

        String relatorio = HistoricoLideranca.montarRelatorio(pasta);

        assertThat(relatorio).contains("A partir de 2026-11-12T12:00:01Z: Agencia 2 e a lider");
        assertThat(relatorio).contains("A partir de 2026-11-12T12:05:01Z: Agencia 1 e a lider");
        assertThat(relatorio.indexOf("Agencia 2 e a lider")).isLessThan(relatorio.indexOf("Agencia 1 e a lider"));
        assertThat(relatorio).contains("[agencia-1] LIDER_NAO_RESPONDEU {\"liderAtual\":2,\"agencia\":1}");
        assertThat(relatorio).contains("sequencia de lideres: 2 -> 1");
    }

    @Test
    @DisplayName("na tempestade da subida, a mesma lider se declarando de novo aparece como reafirmacao, nao como troca")
    void tempestadeSeparaReafirmacaoDeTroca(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-1", "TORNOU_LIDER", "{\"idAgencia\":1}", "2026-11-12T12:00:00Z");
        gravar(pasta, "agencia-2", "TORNOU_LIDER", "{\"idAgencia\":2}", "2026-11-12T12:00:01Z");
        gravar(pasta, "agencia-2", "TORNOU_LIDER", "{\"idAgencia\":2}", "2026-11-12T12:00:02Z");

        String relatorio = HistoricoLideranca.montarRelatorio(pasta);

        assertThat(relatorio).contains("A partir de 2026-11-12T12:00:02Z: Agencia 2 e a lider (reafirma)");
        assertThat(relatorio).contains("declaracoes TORNOU_LIDER: 3 | trocas de lider: 1");
        assertThat(relatorio).contains("sequencia de lideres: 1 -> 2");
    }

    @Test
    @DisplayName("eventos bancarios ficam de fora: so entram os do Bully")
    void ignoraEventosBancarios(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-0", "DEPOSITO", "{\"id\":0,\"valor\":10}", "2026-11-12T12:00:00Z");
        gravar(pasta, "agencia-0", "ELEICAO_INICIADA", "{\"idAgencia\":0}", "2026-11-12T12:00:01Z");

        String relatorio = HistoricoLideranca.montarRelatorio(pasta);

        assertThat(relatorio).doesNotContain("DEPOSITO");
        assertThat(relatorio).contains("ELEICAO_INICIADA");
    }

    @Test
    @DisplayName("sem nenhuma lider declarada, diz isso em vez de imprimir uma linha do tempo vazia")
    void semLider(@TempDir Path pasta) throws IOException {
        gravar(pasta, "agencia-0", "DEPOSITO", "{\"id\":0,\"valor\":10}", "2026-11-12T12:00:00Z");

        assertThat(HistoricoLideranca.montarRelatorio(pasta)).contains("(nenhuma agencia se declarou lider");
    }

    private static void gravar(Path pasta, String agencia, String tipo, String detalhes, String hora)
            throws IOException {
        String linha = "{\"agencia\":\"" + agencia + "\",\"tipo\":\"" + tipo + "\",\"timestampVetorial\":[0,0,0],"
                + "\"horaParede\":\"" + hora + "\",\"detalhes\":" + detalhes + "}\n";
        Files.writeString(pasta.resolve("eventos-" + agencia + ".jsonl"), linha, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
