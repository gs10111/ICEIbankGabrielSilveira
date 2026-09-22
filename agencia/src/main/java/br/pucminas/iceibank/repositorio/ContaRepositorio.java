package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.modelo.conta.Conta;
import org.springframework.stereotype.Repository;

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
 *
 * NAO existe "salvar depois de alterar". O mapa guarda a REFERENCIA da Conta, entao
 * conta.depositar(...) ja altera o objeto que esta aqui dentro — um put() depois seria
 * substituir o objeto por ele mesmo. O metodo se chama `inserir` porque e so isso que
 * ele faz: cadastrar uma conta que ainda nao existe.
 *
 * A protecao de concorrencia mora na propria Conta (synchronized), nao aqui: o
 * ConcurrentHashMap protege o MAPA, nao o saldo de dentro de cada conta.
 */
@Repository
public class ContaRepositorio {

    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();

    public Optional<Conta> buscar(int id) {
        return Optional.ofNullable(contas.get(id));
    }

    /** Cadastra uma conta nova. Quem chama ja conferiu que ela nao existe. */
    public void inserir(Conta conta) {
        contas.put(conta.id(), conta);
    }

    public boolean existe(int id) {
        return contas.containsKey(id);
    }

    public Collection<Conta> todas() {
        return Collections.unmodifiableCollection(contas.values());
    }
}
