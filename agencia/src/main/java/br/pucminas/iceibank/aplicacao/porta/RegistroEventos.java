package br.pucminas.iceibank.aplicacao.porta;

import br.pucminas.iceibank.dominio.evento.Evento;
import br.pucminas.iceibank.dominio.relogio.Carimbo;

import java.util.Map;

/**
 * Porta de saida: onde os eventos ficam registrados.
 *
 * Sprint 1: arquivo .jsonl (uma linha JSON por evento).
 * Sprint 2: topico de mensageria.
 */
public interface RegistroEventos {

    Evento registrar(String tipo, Carimbo carimbo, Map<String, Object> detalhes);
}
