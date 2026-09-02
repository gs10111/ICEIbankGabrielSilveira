package br.pucminas.iceibank.infra.arquivo;

import br.pucminas.iceibank.aplicacao.porta.RegistroEventos;
import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.relogio.Carimbo;
import br.pucminas.iceibank.dominio.relogio.CarimboLamport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter de saida: grava cada evento como UMA linha JSON (formato .jsonl).
 *
 * Uma linha por evento permite que o mesclar-logs (Parte E) leia os arquivos
 * das 3 agencias linha a linha, sem carregar tudo na memoria.
 */
public class RegistroEventosJsonl implements RegistroEventos {

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

    public Path caminhoArquivo() {
        return caminhoArquivo;
    }

    private void escrever(Evento evento) {
        Map<String, Object> linha = new LinkedHashMap<>();
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

    /**
     * O formato do carimbo e detalhe de SERIALIZACAO, entao mora aqui na infra,
     * nao no dominio. No Sprint 2, quando CarimboVetorial entrar no `permits`,
     * este switch para de compilar e aponta exatamente onde tratar o caso novo.
     */
    private static Object valorSerializavel(Carimbo carimbo) {
        return switch (carimbo) {
            case CarimboLamport(int valor) -> valor;
        };
    }
}
