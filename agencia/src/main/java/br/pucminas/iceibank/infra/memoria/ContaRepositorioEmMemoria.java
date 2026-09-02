package br.pucminas.iceibank.infra.memoria;

import br.pucminas.iceibank.aplicacao.porta.ContaRepositorio;
import br.pucminas.iceibank.dominio.conta.Conta;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter de saida: guarda as contas em memoria.
 *
 * ConcurrentHashMap porque o Tomcat atende cada requisicao numa thread do pool —
 * duas requisicoes simultaneas podem mexer no mapa ao mesmo tempo.
 *
 * Nao ha persistencia: reiniciar a agencia perde as contas. E o esperado no Sprint 1.
 */
public class ContaRepositorioEmMemoria implements ContaRepositorio {

    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();

    @Override
    public Optional<Conta> buscar(int id) {
        return Optional.ofNullable(contas.get(id));
    }

    @Override
    public void salvar(Conta conta) {
        contas.put(conta.id(), conta);
    }

    @Override
    public boolean existe(int id) {
        return contas.containsKey(id);
    }

    @Override
    public Collection<Conta> todas() {
        return Collections.unmodifiableCollection(contas.values());
    }
}
