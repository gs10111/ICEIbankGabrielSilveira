package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.servico.ConsultaEventos;
import br.pucminas.iceibank.servico.RegistroEventos;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.CarimboLamport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter de saida: grava cada evento como UMA linha JSON (formato .jsonl)
 * e sabe reler o arquivo para montar o historico de uma conta.
 *
 * Implementa DUAS portas (escrita e leitura). Uma classe pode implementar
 * varias interfaces pequenas — quem consome enxerga so a que precisa.
 */
public class RegistroEventosJsonl implements RegistroEventos, ConsultaEventos {

    /** Campos de "detalhes" que referenciam uma conta, usados para filtrar o historico. */
    private static final List<String> CAMPOS_DE_CONTA = List.of("id", "idConta", "idOrigem", "idDestino");

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper json = new ObjectMapper();

    public RegistroEventosJsonl(String nomeAgencia, Path pastaDados) {
        this.nomeAgencia = nomeAgencia;
        try {
            Files.createDirectories(pastaDados);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel criar a pasta de dados: " + pastaDados, e);
        }
        this.caminhoArquivo = pastaDados.resolve("eventos-" + nomeAgencia + ".jsonl");
    }

    @Override
    public synchronized Evento registrar(String tipo, Carimbo carimbo, Map<String, Object> detalhes) {
        Evento evento = new Evento(nomeAgencia, tipo, carimbo, Instant.now(), detalhes);
        escrever(evento);
        System.out.println("[Lamport " + valorSerializavel(carimbo) + "] " + tipo + " " + detalhes);
        return evento;
    }

    @Override
    public synchronized List<Evento> ultimosDaConta(int idConta, int limite) {
        List<Evento> daConta = new ArrayList<>();
        for (Map<String, Object> linha : lerTodasAsLinhas()) {
            Evento evento = paraEvento(linha);
            if (envolveAConta(evento, idConta)) {
                daConta.add(evento);
            }
        }
        Collections.reverse(daConta);                       // mais recente primeiro
        return daConta.size() > limite ? List.copyOf(daConta.subList(0, limite)) : List.copyOf(daConta);
    }

    @Override
    public synchronized List<Evento> ultimos(int limite) {
        List<Evento> todos = new ArrayList<>();
        for (Map<String, Object> linha : lerTodasAsLinhas()) {
            todos.add(paraEvento(linha));
        }
        Collections.reverse(todos);
        return todos.size() > limite ? List.copyOf(todos.subList(0, limite)) : List.copyOf(todos);
    }

    @Override
    public synchronized int quantidade() {
        return lerTodasAsLinhas().size();
    }

    public Path caminhoArquivo() {
        return caminhoArquivo;
    }

    // ------------------------------------------------------------------ escrita

    private void escrever(Evento evento) {
        Map<String, Object> linha = new LinkedHashMap<>();   // LinkedHashMap preserva a ordem dos campos
        linha.put("agencia", evento.agencia());
        linha.put("tipo", evento.tipo());
        linha.put("timestampLamport", valorSerializavel(evento.carimbo()));
        linha.put("horaParede", evento.horaParede().toString());
        linha.put("detalhes", evento.detalhes());

        try {
            Files.writeString(caminhoArquivo,
                    json.writeValueAsString(linha) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar evento em " + caminhoArquivo, e);
        }
    }

    // ------------------------------------------------------------------ leitura

    private List<Map<String, Object>> lerTodasAsLinhas() {
        if (!Files.exists(caminhoArquivo)) {
            return List.of();
        }
        List<Map<String, Object>> linhas = new ArrayList<>();
        try {
            for (String linha : Files.readAllLines(caminhoArquivo, StandardCharsets.UTF_8)) {
                if (!linha.isBlank()) {
                    linhas.add(json.readValue(linha, new TypeReference<Map<String, Object>>() { }));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler eventos de " + caminhoArquivo, e);
        }
        return linhas;
    }

    @SuppressWarnings("unchecked")
    private Evento paraEvento(Map<String, Object> linha) {
        int timestamp = ((Number) linha.get("timestampLamport")).intValue();
        Map<String, Object> detalhes = (Map<String, Object>) linha.getOrDefault("detalhes", Map.of());
        return new Evento(
                (String) linha.get("agencia"),
                (String) linha.get("tipo"),
                new CarimboLamport(timestamp),
                Instant.parse((String) linha.get("horaParede")),
                detalhes);
    }

    private boolean envolveAConta(Evento evento, int idConta) {
        for (String campo : CAMPOS_DE_CONTA) {
            Object valor = evento.detalhes().get(campo);
            if (valor instanceof Number numero && numero.intValue() == idConta) {
                return true;
            }
        }
        return false;
    }

    /**
     * Formato do carimbo e detalhe de SERIALIZACAO — mora na infra, nao no dominio.
     * Sendo `sealed`, no Sprint 2 este switch para de compilar e aponta onde tratar o vetorial.
     */
    private static Object valorSerializavel(Carimbo carimbo) {
        return switch (carimbo) {
            case CarimboLamport(int valor) -> valor;
        };
    }
}
