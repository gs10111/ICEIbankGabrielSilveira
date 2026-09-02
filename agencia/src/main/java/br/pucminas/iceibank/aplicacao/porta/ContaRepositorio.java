package br.pucminas.iceibank.aplicacao.porta;

import br.pucminas.iceibank.dominio.conta.Conta;

import java.util.Collection;
import java.util.Optional;

/**
 * Porta de saida: onde as contas desta agencia vivem.
 *
 * Sprint 1: Map em memoria. Sprint 4: banco de dados.
 * Os casos de uso nao sabem a diferenca.
 */
public interface ContaRepositorio {

    Optional<Conta> buscar(int id);

    void salvar(Conta conta);

    boolean existe(int id);

    Collection<Conta> todas();
}
