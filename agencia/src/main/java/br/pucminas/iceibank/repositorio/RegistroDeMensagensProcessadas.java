package br.pucminas.iceibank.repositorio;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FUNCIONALIDADE ADICIONAL DO SPRINT 2 — quais mensagens esta agencia ja processou.
 *
 * O RabbitMQ entrega AT-LEAST-ONCE, nao exactly-once. Se o ack se perder no caminho
 * de volta (rede, ou o consumidor morrendo entre aplicar o credito e confirmar), o
 * broker reentrega uma mensagem que ja foi aplicada. Sem esta memoria, o credito
 * entra duas vezes e ninguem percebe: o cliente so ve dinheiro a mais.
 *
 * `Set.add` devolve false quando o elemento ja estava la, e o faz ATOMICAMENTE. Um
 * `if (contem) ... else adiciona(...)` deixaria passar duas entregas simultaneas da
 * mesma mensagem — e o container do Spring AMQP usa varias threads.
 *
 * Mora em memoria, como o resto do estado desta agencia. Reiniciar esquece tudo,
 * entao uma reentrega que atravesse um restart ainda credita duas vezes: e o mesmo
 * limite que faz as contas sumirem, e o Sprint 4 resolve os dois de uma vez, com
 * estado duravel.
 */
@Component
public class RegistroDeMensagensProcessadas {

    private final Set<String> jaVistas = ConcurrentHashMap.newKeySet();

    /**
     * Marca a mensagem como processada e diz se ela era inedita.
     *
     * Devolve `true` na primeira vez e `false` em toda reentrega. Uma operacao so,
     * de proposito: perguntar e marcar em chamadas separadas reabriria a corrida que
     * o metodo existe para fechar.
     */
    public boolean registrarSeInedita(String identidade) {
        return jaVistas.add(identidade);
    }

    public int quantidade() {
        return jaVistas.size();
    }
}
