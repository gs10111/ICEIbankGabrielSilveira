package br.pucminas.iceibank.modelo.relogio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;

class RelogioLamportTest {
    private RelogioLamport relogio;

    @BeforeEach
    void criarRelogioNovo() {
        relogio = new RelogioLamport();
    }

    @Test
    @DisplayName("relogio novo: primeiro evento local carimba 1")
    void primeiroEventoLocalCarimbaUm() {

        // Act
        Carimbo carimbo = relogio.eventoLocal();

        // Assert
        assertEquals(new CarimboLamport(1), carimbo);
    }

    @Test
    @DisplayName("dois eventos locais carimbam 1 e depois 2")
    void doisEventoslocaisIncrementam() {

        Carimbo primeiro = relogio.eventoLocal();
        Carimbo segundo = relogio.eventoLocal();

        assertEquals(new CarimboLamport(1), primeiro);
        assertEquals(new CarimboLamport(2), segundo);

    }

    @Test
    @DisplayName("aoEnviar e eventoLocal compartilham o mesmo contador")
    void aoEnviarCompartilhaOMesmoContadorDoEventoLocal() {

        Carimbo local = relogio.eventoLocal();
        Carimbo envio = relogio.aoEnviar();

        assertEquals(new CarimboLamport(1), local);
        assertEquals(new CarimboLamport(2), envio);
    }

    @Test
    @DisplayName("ao receber carimbo maior que o local, adota o maior e soma 1")
    void aoReceberCarimboMaiorSalta() {
        // Arrange: deixe o contador local em 2
        relogio.eventoLocal();
        relogio.eventoLocal();

        // Act: chega uma mensagem de outra agencia carimbada com 7
        Carimbo resultado = relogio.aoReceber(new CarimboLamport(7));

        // Assert
        assertEquals(new CarimboLamport(8), resultado);
    }

    @Test
    @DisplayName("ao receber carimbo menor que o local, o relogio nao retrocede")
    void aoReceberCarimboMenorNaoRetrocede() {
        // Arrange: deixe o contador local em 10
        for (int i = 0; i < 10; i++) {
            relogio.eventoLocal();
        }

        // Act: chega uma mensagem de uma agencia atrasada, carimbada com 3
        Carimbo resultado = relogio.aoReceber(new CarimboLamport(3));

        // Assert
        assertEquals(new CarimboLamport(11), resultado);
    }

    @Test
    @DisplayName("ao receber carimbo igual que o local, o relogio nao empata")
    void aoReceberCarimboIgualDesempata() {
        // Arrange: deixe o contador local em 4
        for (int i = 0; i < 5; i++) {
            relogio.eventoLocal();
        }

        // Act: chega uma mensagem de uma agencia, carimbada com 5
        Carimbo resultado = relogio.aoReceber(new CarimboLamport(5));

        // Assert
        assertEquals(new CarimboLamport(6), resultado);
    }

    @Test
    @DisplayName("cem threads simultaneas geram cem carimbos distintos")
    void eventoLocalEhSeguroSobConcorrencia() throws InterruptedException {
        int totalThreads = 100;
        Set<Carimbo> carimbos = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(totalThreads);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                try {
                    largada.await(); // todas esperam aqui
                    carimbos.add(relogio.eventoLocal());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown(); // dispara todas de uma vez
        chegada.await(); // espera todas terminarem
        pool.shutdown();

        assertEquals(totalThreads, carimbos.size(),
                "carimbos repetidos: o contador nao e thread-safe");
    }

}
