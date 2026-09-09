package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.CarimboLamport;

import java.util.Map;

public record EventoResposta(String agencia, String tipo, int timestampLamport,
                             String horaParede, Map<String, Object> detalhes) {

    public static EventoResposta de(Evento evento) {
        return new EventoResposta(evento.agencia(), evento.tipo(), valorDe(evento.carimbo()),
                evento.horaParede().toString(), evento.detalhes());
    }

    private static int valorDe(Carimbo carimbo) {
        return switch (carimbo) {
            case CarimboLamport(int valor) -> valor;
        };
    }
}
