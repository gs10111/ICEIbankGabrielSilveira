package br.pucminas.iceibank.modelo.relogio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Relogio vetorial — Sprint 2, Parte B.
 *
 * As tres regras (roteiro, secao 6):
 *   1. evento local  -> incrementa a PROPRIA posicao
 *   2. ao enviar     -> incrementa a propria posicao e anexa o vetor inteiro
 *   3. ao receber V  -> vetor[i] = max(vetor[i], V[i]) em cada posicao, DEPOIS incrementa a propria
 */
class RelogioVetorialTest {

    private static CarimboVetorial vetor(int... valores) {
        return new CarimboVetorial(java.util.Arrays.stream(valores).boxed().toList());
    }

    @Nested
    @DisplayName("regra 1 — evento local")
    class EventoLocal {

        @Test
        @DisplayName("relogio novo de 3 agencias comeca zerado")
        void comecaZerado() {
            RelogioVetorial relogio = new RelogioVetorial(0, 3);

            assertEquals(vetor(0, 0, 0), relogio.valorAtual());
        }

        @Test
        @DisplayName("incrementa SO a propria posicao")
        void incrementaSoAPropriaPosicao() {
            RelogioVetorial daAgencia1 = new RelogioVetorial(1, 3);

            assertEquals(vetor(0, 1, 0), daAgencia1.eventoLocal());
        }

        @Test
        @DisplayName("dois eventos locais somam na propria posicao")
        void doisEventosLocais() {
            RelogioVetorial relogio = new RelogioVetorial(0, 3);

            relogio.eventoLocal();

            assertEquals(vetor(2, 0, 0), relogio.eventoLocal());
        }
    }

    @Nested
    @DisplayName("regra 2 — ao enviar")
    class AoEnviar {

        @Test
        @DisplayName("tambem incrementa a propria posicao, no MESMO vetor")
        void compartilhaOVetorComOEventoLocal() {
            RelogioVetorial relogio = new RelogioVetorial(0, 3);

            relogio.eventoLocal();

            assertEquals(vetor(2, 0, 0), relogio.aoEnviar());
        }
    }

    @Nested
    @DisplayName("regra 3 — ao receber")
    class AoReceber {

        @Test
        @DisplayName("max posicao a posicao e depois incrementa a propria")
        void maxPosicaoAPosicaoEDepoisIncrementa() {
            // A agencia 1 tem [0,1,0] e recebe [2,0,0] da agencia 0.
            // max -> [2,1,0]; incrementa a posicao 1 -> [2,2,0]
            RelogioVetorial daAgencia1 = new RelogioVetorial(1, 3);
            daAgencia1.eventoLocal();

            assertEquals(vetor(2, 2, 0), daAgencia1.aoReceber(vetor(2, 0, 0)));
        }

        @Test
        @DisplayName("nunca retrocede: recebido menor so incrementa a propria")
        void recebidoMenorNaoRetrocede() {
            RelogioVetorial daAgencia0 = new RelogioVetorial(0, 3);
            daAgencia0.eventoLocal();
            daAgencia0.eventoLocal();
            daAgencia0.eventoLocal();                       // [3,0,0]

            assertEquals(vetor(4, 0, 0), daAgencia0.aoReceber(vetor(1, 0, 0)));
        }

        @Test
        @DisplayName("aprende as posicoes das outras agencias que ainda nao conhecia")
        void aprendeAsPosicoesDasOutras() {
            RelogioVetorial daAgencia2 = new RelogioVetorial(2, 3);

            // recebe um vetor que ja viu a agencia 0 e a 1
            assertEquals(vetor(5, 7, 1), daAgencia2.aoReceber(vetor(5, 7, 0)));
        }

        @Test
        @DisplayName("vetor recebido de tamanho errado e recusado")
        void vetorDeTamanhoErradoEhRecusado() {
            RelogioVetorial relogio = new RelogioVetorial(0, 3);

            assertThrows(IllegalArgumentException.class, () -> relogio.aoReceber(vetor(1, 1)));
        }
    }

    @Nested
    @DisplayName("construcao")
    class Construcao {

        @Test
        @DisplayName("id fora da malha e recusado")
        void idForaDaMalha() {
            assertThrows(IllegalArgumentException.class, () -> new RelogioVetorial(3, 3));
            assertThrows(IllegalArgumentException.class, () -> new RelogioVetorial(-1, 3));
        }

        @Test
        @DisplayName("malha vazia e recusada")
        void malhaVazia() {
            assertThrows(IllegalArgumentException.class, () -> new RelogioVetorial(0, 0));
        }

        @Test
        @DisplayName("restaurado de um vetor conhecido, continua de la")
        void restauradoContinuaDeOndeParou() {
            // mesmo motivo do Sprint 1: o .jsonl sobrevive ao restart, o vetor em memoria nao
            RelogioVetorial restaurado = new RelogioVetorial(0, vetor(11, 4, 2));

            assertEquals(vetor(12, 4, 2), restaurado.eventoLocal());
        }
    }

    @Nested
    @DisplayName("concorrencia")
    class Concorrencia {

        @Test
        @DisplayName("cem eventos locais simultaneos geram cem vetores distintos")
        void eventoLocalEhSeguroSobConcorrencia() throws InterruptedException {
            RelogioVetorial relogio = new RelogioVetorial(0, 3);
            int totalThreads = 100;
            Set<CarimboVetorial> carimbos = ConcurrentHashMap.newKeySet();
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch largada = new CountDownLatch(1);
            CountDownLatch chegada = new CountDownLatch(totalThreads);

            for (int i = 0; i < totalThreads; i++) {
                pool.submit(() -> {
                    try {
                        largada.await();
                        carimbos.add(relogio.eventoLocal());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        chegada.countDown();
                    }
                });
            }

            largada.countDown();
            chegada.await();
            pool.shutdown();

            assertEquals(totalThreads, carimbos.size(), "vetores repetidos: o relogio nao e thread-safe");
        }
    }

    @Nested
    @DisplayName("comparacao de vetores — o que Lamport nao conseguia responder")
    class Comparacao {

        @Test
        @DisplayName("[3,1,0] vs [3,2,0] -> ANTES (questao 6.4.2 do roteiro)")
        void v1AntesDeV2() {
            assertThat(CarimboVetorial.comparar(vetor(3, 1, 0), vetor(3, 2, 0))).isEqualTo(Relacao.ANTES);
            assertThat(CarimboVetorial.comparar(vetor(3, 2, 0), vetor(3, 1, 0))).isEqualTo(Relacao.DEPOIS);
        }

        @Test
        @DisplayName("[3,1,0] vs [1,3,0] -> CONCORRENTES (questao 6.4.3 do roteiro)")
        void concorrentes() {
            assertThat(CarimboVetorial.comparar(vetor(3, 1, 0), vetor(1, 3, 0))).isEqualTo(Relacao.CONCORRENTES);
        }

        @Test
        @DisplayName("vetores identicos -> IGUAIS")
        void iguais() {
            assertThat(CarimboVetorial.comparar(vetor(2, 2, 2), vetor(2, 2, 2))).isEqualTo(Relacao.IGUAIS);
        }

        @Test
        @DisplayName("a relacao e simetrica: inverter os argumentos inverte ANTES e DEPOIS")
        void simetria() {
            CarimboVetorial a = vetor(1, 0, 0);
            CarimboVetorial b = vetor(1, 1, 0);

            assertThat(CarimboVetorial.comparar(a, b)).isEqualTo(Relacao.ANTES);
            assertThat(CarimboVetorial.comparar(b, a)).isEqualTo(Relacao.DEPOIS);
            assertThat(CarimboVetorial.comparar(a, a)).isEqualTo(Relacao.IGUAIS);
        }

        @Test
        @DisplayName("uma transferencia entre agencias NAO produz par concorrente")
        void transferenciaEntreAgenciasEhCausal() {
            // agencia 0 debita e envia; agencia 1 recebe. O credito tem de ser DEPOIS do debito.
            RelogioVetorial origem = new RelogioVetorial(0, 3);
            RelogioVetorial destino = new RelogioVetorial(1, 3);

            CarimboVetorial debito = origem.eventoLocal();
            CarimboVetorial envio = origem.aoEnviar();
            CarimboVetorial creditoRemoto = destino.aoReceber(envio);

            assertThat(CarimboVetorial.comparar(debito, creditoRemoto)).isEqualTo(Relacao.ANTES);
        }

        @Test
        @DisplayName("duas agencias trabalhando sozinhas produzem par CONCORRENTE")
        void agenciasIndependentesSaoConcorrentes() {
            RelogioVetorial agencia0 = new RelogioVetorial(0, 3);
            RelogioVetorial agencia1 = new RelogioVetorial(1, 3);

            CarimboVetorial criouNa0 = agencia0.eventoLocal();
            CarimboVetorial criouNa1 = agencia1.eventoLocal();

            assertThat(CarimboVetorial.comparar(criouNa0, criouNa1)).isEqualTo(Relacao.CONCORRENTES);
        }

        @Test
        @DisplayName("comparar vetores de tamanhos diferentes e recusado")
        void tamanhosDiferentes() {
            assertThrows(IllegalArgumentException.class, () -> CarimboVetorial.comparar(vetor(1, 0, 0), vetor(1, 0)));
        }
    }

    @Nested
    @DisplayName("CarimboVetorial")
    class Carimbo {

        @Test
        @DisplayName("e imutavel: a lista de origem nao pode alterar o carimbo depois")
        void ehImutavel() {
            List<Integer> origem = new java.util.ArrayList<>(List.of(1, 2, 3));
            CarimboVetorial carimbo = new CarimboVetorial(origem);

            origem.set(0, 99);

            assertEquals(vetor(1, 2, 3), carimbo);
        }

        @Test
        @DisplayName("igualdade por valor, para poder viver em Set e Map")
        void igualdadePorValor() {
            assertThat(vetor(1, 2, 3)).isEqualTo(vetor(1, 2, 3)).hasSameHashCodeAs(vetor(1, 2, 3));
            assertThat(vetor(1, 2, 3)).isNotEqualTo(vetor(1, 2, 4));
        }
    }
}
