package br.pucminas.iceibank.dominio.relogio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelogioLamportTest {

    @Test
    @DisplayName("relogio novo: primeiro evento local carimba 1")
    void primeiroEventoLocalCarimbaUm() {
        // Arrange
        RelogioLamport relogio = new RelogioLamport();

        // Act
        Carimbo carimbo = relogio.eventoLocal();

        // Assert
        assertEquals(new CarimboLamport(1), carimbo);
    }
}
