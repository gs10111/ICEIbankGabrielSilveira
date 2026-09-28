package br.pucminas.iceibank.ferramentas;

import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.Relacao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Linha do tempo unificada das 3 agencias.
 *
 * Sprint 1 (Parte E): ordenava por um inteiro de Lamport e marcava EMPATES.
 * Sprint 2 (Parte B): o carimbo virou VETOR, entao nao existe mais "ordenar por
 * carimbo" — vetores dao ordem PARCIAL, e uma lista impressa e necessariamente
 * uma ordem total. A listagem passa a ser por hora de parede (so para ficar
 * legivel) com o vetor visivel em cada linha; quem responde "quem veio antes" e
 * a comparacao de vetores, que entra na Parte D.
 *
 * Rodar:
 *   java -jar target/iceibank-agencia-1.0.0.jar --mesclar-logs [pasta]
 */
public final class MesclarLogs {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MesclarLogs() {
    }

    public static void main(String[] args) {
        Path pasta = Path.of(args.length > 0 ? args[0] : "data");
        System.out.print(montarRelatorio(pasta));
    }

    public static String montarRelatorio(Path pasta) {
        List<Map<String, Object>> eventos = lerTodos(pasta);
        StringBuilder saida = new StringBuilder();

        saida.append("=== Linha do tempo unificada (relogio vetorial) ===\n");
        saida.append("pasta: ").append(pasta.toAbsolutePath()).append('\n');
        if (eventos.isEmpty()) {
            saida.append("\nNenhum evento encontrado. Rode as agencias e faca algumas operacoes primeiro.\n");
            return saida.toString();
        }

        // Ordenada por hora de parede APENAS para exibicao. A hora fisica nao carrega
        // causalidade e nao decide nada no sistema — quem responde isso e o vetor.
        eventos.sort(Comparator.comparing(e -> String.valueOf(e.get("horaParede"))));

        saida.append('\n');
        for (Map<String, Object> evento : eventos) {
            saida.append(String.format("%-12s (%s) %-10s - %-30s %s%n",
                    vetorDe(evento),
                    evento.get("horaParede"),
                    evento.get("agencia"),
                    evento.get("tipo"),
                    evento.get("detalhes")));
        }

        List<String> concorrentes = paresConcorrentes(eventos);
        saida.append("\n=== Pares de eventos CONCORRENTES (agencias diferentes) ===\n");
        if (concorrentes.isEmpty()) {
            // Lista vazia nao e defeito: e o resultado de todo evento estar numa cadeia
            // causal. Dizer isso em voz alta evita ler o silencio como bug.
            saida.append("(nenhum par concorrente nesta execucao - gere operacoes independentes"
                    + " em agencias diferentes e rode de novo)\n");
        }
        for (String par : concorrentes) {
            saida.append(par).append('\n');
        }

        saida.append("\n=== Resumo ===\n");
        saida.append("eventos: ").append(eventos.size())
                .append(" | agencias: ").append(eventos.stream().map(e -> e.get("agencia")).distinct().count())
                .append('\n');
        return saida.toString();
    }

    /**
     * PARTE D — os pares comprovadamente concorrentes, entre agencias DIFERENTES.
     *
     * Pares da MESMA agencia sao pulados de proposito: dentro de uma agencia o
     * relogio e um so e sempre incrementa, entao dois eventos dela nunca podem ser
     * concorrentes. Listar isso seria ruido garantido.
     *
     * O laco e O(n^2) — compara todos os pares. E o que o roteiro pede e o que a
     * pergunta 8.3.3 problematiza: com milhoes de eventos isso nao se sustenta.
     */
    static List<String> paresConcorrentes(List<Map<String, Object>> eventos) {
        List<String> pares = new ArrayList<>();
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                Map<String, Object> primeiro = eventos.get(i);
                Map<String, Object> segundo = eventos.get(j);
                if (mesmaAgencia(primeiro, segundo) || !saoConcorrentes(primeiro, segundo)) {
                    continue;
                }
                pares.add(descrever(primeiro) + "  x  " + descrever(segundo));
            }
        }
        return pares;
    }

    private static boolean mesmaAgencia(Map<String, Object> primeiro, Map<String, Object> segundo) {
        return String.valueOf(primeiro.get("agencia")).equals(String.valueOf(segundo.get("agencia")));
    }

    /**
     * Linha sem vetor (log truncado no meio de uma escrita) ou de malha com outro
     * numero de agencias nao da para comparar. Nao afirmamos concorrencia sem vetor:
     * "nao sei" e diferente de "sao independentes".
     */
    private static boolean saoConcorrentes(Map<String, Object> primeiro, Map<String, Object> segundo) {
        List<Integer> umVetor = vetorDe(primeiro);
        List<Integer> outroVetor = vetorDe(segundo);
        if (umVetor.isEmpty() || umVetor.size() != outroVetor.size()) {
            return false;
        }
        return CarimboVetorial.comparar(new CarimboVetorial(umVetor), new CarimboVetorial(outroVetor))
                == Relacao.CONCORRENTES;
    }

    private static String descrever(Map<String, Object> evento) {
        return "[" + evento.get("agencia") + "] " + evento.get("tipo") + " " + vetorDe(evento);
    }

    /** O vetor da linha, no formato [a, b, c]. Linha sem o campo aparece como [?]. */
    @SuppressWarnings("unchecked")
    static List<Integer> vetorDe(Map<String, Object> evento) {
        Object bruto = evento.get("timestampVetorial");
        if (!(bruto instanceof List<?> lista)) {
            return List.of();
        }
        return ((List<Number>) lista).stream().map(Number::intValue).toList();
    }

    private static List<Map<String, Object>> lerTodos(Path pasta) {
        if (!Files.isDirectory(pasta)) {
            throw new IllegalArgumentException("pasta de dados nao encontrada: " + pasta.toAbsolutePath());
        }
        List<Map<String, Object>> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pasta)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (!linha.isBlank()) {
                        eventos.add(JSON.readValue(linha, new TypeReference<Map<String, Object>>() { }));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler os logs em " + pasta, e);
        }
        return eventos;
    }

    private static int inteiro(Object valor) {
        return ((Number) valor).intValue();
    }

    static Instant horaDe(Map<String, Object> evento) {
        return Instant.parse(String.valueOf(evento.get("horaParede")));
    }
}
