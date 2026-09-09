package br.pucminas.iceibank.aplicacao.porta;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Porta de saida: LER uma conta de outra agencia.
 *
 * Separada de AgenciaRemota (que escreve) pelo mesmo motivo que ConsultaEventos
 * e separada de RegistroEventos: quem so transfere nao precisa conhecer leitura
 * remota, e quem so consolida nao precisa poder creditar (Interface Segregation).
 */
public interface ConsultaContaRemota {

    /** Vazio se a conta nao existe la; excecao se a agencia nao respondeu. */
    Optional<ContaRemota> consultar(int idAgencia, int idConta);

    record ContaRemota(int id, String nomeAluno, BigDecimal saldo, int agencia) { }
}
