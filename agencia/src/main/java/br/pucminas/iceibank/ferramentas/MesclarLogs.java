package br.pucminas.iceibank.ferramentas;

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
 * PARTE E — linha do tempo unificada.
 *
 * Le os .jsonl das 3 agencias e monta UMA sequencia ordenada por relogio de Lamport.
 * Marca os EMPATES (mesmo timestamp em agencias diferentes), que sao a evidencia
 * de que Lamport define ordem PARCIAL, nao total: timestamps iguais nao dizem qual
 * evento veio antes — dizem que nenhum causou o outro.
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

        saida.append("=== Linha do tempo unificada (ordenada por relogio de Lamport) ===\n");
        saida.append("pasta: ").append(pasta.toAbsolutePath()).append('\n');
        if (eventos.isEmpty()) {
            saida.append("\nNenhum evento encontrado. Rode as agencias e faca algumas operacoes primeiro.\n");
            return saida.toString();
        }

        // Desempate ESTAVEL por hora de parede so para a exibicao ficar legivel.
        // O criterio de ordenacao continua sendo o relogio logico; a hora fisica
        // NAO carrega causalidade e nao decide nada no sistema.
        eventos.sort(Comparator
                .<Map<String, Object>>comparingInt(e -> inteiro(e.get("timestampLamport")))
                .thenComparing(e -> String.valueOf(e.get("horaParede"))));

        Map<Integer, List<Map<String, Object>>> porTimestamp = new LinkedHashMap<>();
        for (Map<String, Object> evento : eventos) {
            porTimestamp.computeIfAbsent(inteiro(evento.get("timestampLamport")), k -> new ArrayList<>()).add(evento);
        }

        saida.append('\n');
        for (Map<String, Object> evento : eventos) {
            int lamport = inteiro(evento.get("timestampLamport"));
            boolean empatado = porTimestamp.get(lamport).size() > 1;
            saida.append(String.format("[Lamport %3d]%s (%s) %-10s - %-30s %s%n",
                    lamport,
                    empatado ? " <EMPATE>" : "        ",
                    evento.get("horaParede"),
                    evento.get("agencia"),
                    evento.get("tipo"),
                    evento.get("detalhes")));
        }

        // ------- analise dos empates -------
        List<Map.Entry<Integer, List<Map<String, Object>>>> empates = porTimestamp.entrySet().stream()
                .filter(entrada -> entrada.getValue().size() > 1)
                .toList();

        saida.append("\n=== Analise ===\n");
        saida.append("eventos: ").append(eventos.size())
                .append(" | agencias: ").append(eventos.stream().map(e -> e.get("agencia")).distinct().count())
                .append(" | timestamps empatados: ").append(empates.size()).append('\n');

        if (empates.isEmpty()) {
            saida.append("""
                    
                    Nenhum empate nesta amostra. Para produzir um: rode uma operacao em
                    cada agencia quase ao mesmo tempo (elas nao trocam mensagem, entao os
                    contadores avancam de forma independente e colidem).
                    """);
            return saida.toString();
        }

        for (Map.Entry<Integer, List<Map<String, Object>>> empate : empates) {
            saida.append("\nLamport ").append(empate.getKey()).append(" — ")
                    .append(empate.getValue().size()).append(" eventos:\n");
            for (Map<String, Object> evento : empate.getValue()) {
                saida.append("   ").append(evento.get("agencia")).append(" ")
                        .append(evento.get("tipo")).append("  (hora de parede: ")
                        .append(evento.get("horaParede")).append(")\n");
            }
            boolean mesmaAgencia = empate.getValue().stream()
                    .map(e -> String.valueOf(e.get("agencia"))).distinct().count() == 1;
            saida.append(mesmaAgencia
                    ? "   -> MESMA agencia com carimbos iguais: isso seria BUG (contador nao thread-safe).\n"
                    : "   -> Agencias diferentes: eventos CONCORRENTES. Nenhum causou o outro,\n"
                      + "      e Lamport, por construcao, nao consegue ordena-los. A hora de parede\n"
                      + "      sugere uma ordem, mas ela nao significa causalidade — relogios fisicos\n"
                      + "      de maquinas distintas nao estao sincronizados.\n");
        }

        saida.append("""
                
                Conclusao: o relogio de Lamport garante que A -> B implica ts(A) < ts(B),
                mas NAO a volta. Dado ts(A) < ts(B), A pode ter causado B ou os dois podem
                ser concorrentes — nao da para distinguir. E essa lacuna que o relogio
                vetorial do Sprint 2 fecha.
                """);
        return saida.toString();
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
