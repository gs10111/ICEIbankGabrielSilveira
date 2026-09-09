package br.pucminas.iceibank.aplicacao.porta;

import br.pucminas.iceibank.dominio.evento.Evento;

import java.util.List;

/**
 * Porta de LEITURA do log de eventos — separada da porta de escrita (RegistroEventos).
 *
 * Por que duas interfaces e nao uma so com 2 metodos?
 * Interface Segregation (o I do SOLID): quem so escreve (ContaService, TransferenciaService)
 * nao deve ser obrigado a conhecer a operacao de leitura, e vice-versa.
 * E a mesma ideia por tras de CQRS: o caminho de escrita e o de leitura tem
 * responsabilidades, formatos e ate armazenamentos diferentes.
 */
public interface ConsultaEventos {

    /** Ultimos eventos que envolvem esta conta, do mais recente para o mais antigo. */
    List<Evento> ultimosDaConta(int idConta, int limite);

    /** Ultimos eventos desta agencia, do mais recente para o mais antigo. */
    List<Evento> ultimos(int limite);

    /** Quantos eventos esta agencia ja registrou. */
    int quantidade();
}
