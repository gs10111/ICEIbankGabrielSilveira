package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RegistroDeEventos {
    private static final List<String> CAMPOS_DE_CONTA = List.of("id", "idConta", "idOrigem", "idDestino");

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper json = new ObjectMapper();

    public RegistroDeEventos(AgenciaProperties propriedades) {
        this.nomeAgencia = propriedades.nome();
        Path pastaDados = Path.of(propriedades.pastaDados());
        try {
            Files.createDirectories(pastaDados);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel criar a pasta de dados: " + pastaDados, e);
        }
        this.caminhoArquivo = pastaDados.resolve("eventos-" + nomeAgencia + ".jsonl");
    }

    public synchronized Evento registrar(String tipo, CarimboVetorial carimbo, Map<String, Object> detalhes) {
        Evento evento = new Evento(nomeAgencia, tipo, carimbo, Instant.now(), detalhes);
        escrever(evento);
        System.out.println("[vetor " + carimbo + "] " + tipo + " " + detalhes);
        return evento;
    }

    public synchronized List<Evento> ultimosDaConta(int idConta, int limite) {
        List<Evento> daConta = new ArrayList<>();
        for (Map<String, Object> linha : lerTodasAsLinhas()) {
            Evento evento = paraEvento(linha);
            if (envolveAConta(evento, idConta)) {
                daConta.add(evento);
            }
        }
        Collections.reverse(daConta);
        return daConta.size() > limite ? List.copyOf(daConta.subList(0, limite)) : List.copyOf(daConta);
    }

    public synchronized List<Evento> ultimos(int limite) {
        List<Evento> todos = new ArrayList<>();
        for (Map<String, Object> linha : lerTodasAsLinhas()) {
            todos.add(paraEvento(linha));
        }
        Collections.reverse(todos);
        return todos.size() > limite ? List.copyOf(todos.subList(0, limite)) : List.copyOf(todos);
    }

    public synchronized int quantidade() {
        return lerTodasAsLinhas().size();
    }

    public synchronized CarimboVetorial vetorRestaurado(int totalDeAgencias) {
        int[] maior = new int[totalDeAgencias];
        for (Map<String, Object> linha : lerTodasAsLinhas()) {
            List<Integer> vetor = vetorDe(linha);
            for (int i = 0; i < Math.min(vetor.size(), totalDeAgencias); i++) {
                maior[i] = Math.max(maior[i], vetor.get(i));
            }
        }
        return new CarimboVetorial(Arrays.stream(maior).boxed().toList());
    }

    public Path caminhoArquivo() {
        return caminhoArquivo;
    }

    private void escrever(Evento evento) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("agencia", evento.agencia());
        linha.put("tipo", evento.tipo());
        linha.put("timestampVetorial", evento.carimbo().valores());
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
        List<Integer> vetor = vetorDe(linha);
        Map<String, Object> detalhes = (Map<String, Object>) linha.getOrDefault("detalhes", Map.of());
        return new Evento(
                (String) linha.get("agencia"),
                (String) linha.get("tipo"),
                new CarimboVetorial(vetor),
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

    @SuppressWarnings("unchecked")
    private static List<Integer> vetorDe(Map<String, Object> linha) {
        Object bruto = linha.get("timestampVetorial");
        if (!(bruto instanceof List<?> lista)) {
            return List.of();
        }
        return ((List<Number>) lista).stream().map(Number::intValue).toList();
    }
}
