package br.pucminas.iceibank.modelo.evento;

import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;

import java.time.Instant;
import java.util.Map;

public record Evento(
        String agencia,
        String tipo,
        CarimboVetorial carimbo,
        Instant horaParede,
        Map<String, Object> detalhes) {
}
