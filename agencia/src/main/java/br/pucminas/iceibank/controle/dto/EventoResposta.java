package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.evento.Evento;

import java.util.List;
import java.util.Map;

public record EventoResposta(String agencia, String tipo, List<Integer> timestampVetorial,
                             String horaParede, Map<String, Object> detalhes) {
    public static EventoResposta de(Evento evento) {
        return new EventoResposta(evento.agencia(), evento.tipo(), evento.carimbo().valores(),
                evento.horaParede().toString(), evento.detalhes());
    }
}
