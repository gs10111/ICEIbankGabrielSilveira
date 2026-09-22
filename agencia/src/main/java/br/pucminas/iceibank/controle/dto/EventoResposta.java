package br.pucminas.iceibank.controle.dto;

import br.pucminas.iceibank.modelo.evento.Evento;

import java.util.List;
import java.util.Map;

/**
 * O que a API devolve por evento.
 *
 * `timestampVetorial` e uma LISTA, nao um inteiro — foi a mudanca de contrato do
 * Sprint 2. O frontend le essa lista para mostrar o vetor na linha do tempo.
 */
public record EventoResposta(String agencia, String tipo, List<Integer> timestampVetorial,
                             String horaParede, Map<String, Object> detalhes) {

    public static EventoResposta de(Evento evento) {
        return new EventoResposta(evento.agencia(), evento.tipo(), evento.carimbo().valores(),
                evento.horaParede().toString(), evento.detalhes());
    }
}
