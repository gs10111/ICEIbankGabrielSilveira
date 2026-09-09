package br.pucminas.iceibank.modelo.evento;

import br.pucminas.iceibank.modelo.relogio.Carimbo;

import java.time.Instant;
import java.util.Map;

/**
 * Um fato que aconteceu numa agencia, carimbado com o relogio logico.
 *
 * Guarda DOIS tempos de proposito:
 *  - carimbo    -> relogio logico (Lamport). E o que ordena os eventos do sistema.
 *  - horaParede -> relogio fisico da maquina. Serve SO para comparacao humana;
 *                  nenhuma decisao do sistema depende dele.
 */
public record Evento(
        String agencia,
        String tipo,
        Carimbo carimbo,
        Instant horaParede,
        Map<String, Object> detalhes) {
}
