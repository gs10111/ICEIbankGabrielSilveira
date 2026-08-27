package br.pucminas.iceibank.dominio.relogio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @DisplayName(" ao enviar evento carimba 1")
    void enviarIncrementa(){
        Carimbo primeiro = relogio.aoEnviar();
        assertEquals(new CarimboLamport(1), relogio);
    }
}
