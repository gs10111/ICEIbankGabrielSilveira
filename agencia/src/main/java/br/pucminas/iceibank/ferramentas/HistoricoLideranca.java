package br.pucminas.iceibank.ferramentas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SPRINT 3, Parte C — a linha do tempo de liderancas, reconstruida dos logs das 3 agencias.
 *
 * Equivalente ao historico-lideranca.js do roteiro. Le a mesma pasta de .jsonl que o
 * MesclarLogs, filtra os eventos do Bully e ordena por hora de parede.
 *
 * Por que hora de parede e nao vetor: aqui a pergunta e "quem era a lider a partir de
 * quando", que e uma pergunta sobre o tempo FISICO. Os relogios das 3 agencias sao o
 * do mesmo computador no laboratorio, entao a ordem e confiavel; em maquinas diferentes
 * nao seria (e a razao de existir relogio logico).
 *
 * A mais do roteiro: separa TROCA de lider de REAFIRMACAO. Na tempestade da subida a
 * agencia 2 declara TORNOU_LIDER varias vezes; contar cada declaracao como troca
 * superestimaria a instabilidade (pergunta 7.3).
 *
 * Rodar:
 *   java -jar target/iceibank-agencia-1.0.0.jar --historico-lideranca [pasta]
 */
public final class HistoricoLideranca {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> TIPOS_BULLY = Set.of(
            "ELEICAO_INICIADA", "ELEICAO_RECEBIDA", "TORNOU_LIDER", "LIDER_ANUNCIADO",
            "LIDER_NAO_RESPONDEU", "COORDENADOR_RECUSADO");

    private HistoricoLideranca() {
    }

    public static void main(String[] args) {
        Path pasta = Path.of(args.length > 0 ? args[0] : "data");
        System.out.print(montarRelatorio(pasta));
    }

    public static String montarRelatorio(Path pasta) {
        List<Map<String, Object>> eventos = new ArrayList<>(MesclarLogs.lerTodos(pasta).stream()
                .filter(e -> TIPOS_BULLY.contains(String.valueOf(e.get("tipo"))))
                .toList());
        eventos.sort(Comparator.comparing(MesclarLogs::horaDe));

        StringBuilder saida = new StringBuilder("=== Historico de eventos de eleicao ===\n");
        for (Map<String, Object> evento : eventos) {
            saida.append(String.format("(%s) [%s] %s %s%n",
                    evento.get("horaParede"), evento.get("agencia"), evento.get("tipo"),
                    comoJson(evento.get("detalhes"))));
        }

        saida.append("\n=== Linha do tempo de lideres ===\n");
        List<Integer> sequencia = new ArrayList<>();
        int declaracoes = 0;
        for (Map<String, Object> evento : eventos) {
            if (!"TORNOU_LIDER".equals(evento.get("tipo"))) {
                continue;
            }
            declaracoes++;
            int lider = ((Number) ((Map<?, ?>) evento.get("detalhes")).get("idAgencia")).intValue();
            boolean reafirma = !sequencia.isEmpty() && sequencia.get(sequencia.size() - 1) == lider;
            if (!reafirma) {
                sequencia.add(lider);
            }
            saida.append("A partir de ").append(evento.get("horaParede"))
                    .append(": Agencia ").append(lider).append(" e a lider")
                    .append(reafirma ? " (reafirma)" : "").append('\n');
        }
        if (declaracoes == 0) {
            saida.append("(nenhuma agencia se declarou lider nestes logs - suba as agencias e rode de novo)\n");
        }

        saida.append("\n=== Resumo ===\n");
        saida.append("declaracoes TORNOU_LIDER: ").append(declaracoes)
                .append(" | trocas de lider: ").append(Math.max(0, sequencia.size() - 1)).append('\n');
        saida.append("sequencia de lideres: ")
                .append(String.join(" -> ", sequencia.stream().map(String::valueOf).toList())).append('\n');
        return saida.toString();
    }

    private static String comoJson(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            return String.valueOf(valor);
        }
    }
}
