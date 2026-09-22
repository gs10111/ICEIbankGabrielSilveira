package br.pucminas.iceibank.modelo.evento;

import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;

import java.time.Instant;
import java.util.Map;

/**
 * Um fato que aconteceu numa agencia, carimbado com o relogio logico.
 *
 * Guarda DOIS tempos de proposito:
 *  - carimbo    -> relogio logico VETORIAL. E o que ordena (parcialmente) os eventos.
 *  - horaParede -> relogio fisico da maquina. Serve SO para comparacao humana;
 *                  nenhuma decisao do sistema depende dele.
 */
public record Evento(
        String agencia,
        String tipo,
        CarimboVetorial carimbo,
        Instant horaParede,
        Map<String, Object> detalhes) {
}
