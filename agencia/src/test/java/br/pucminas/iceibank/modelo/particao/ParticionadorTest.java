package br.pucminas.iceibank.modelo.particao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticionadorTest {

    @Test
    @DisplayName("com 3 agencias, a conta pertence a agencia id % 3")
    void distribuiContasPeloRestoDaDivisao() {
        Particionador particionador = new Particionador(3);

        assertEquals(0, particionador.agenciaResponsavel(0));
        assertEquals(1, particionador.agenciaResponsavel(1));
        assertEquals(2, particionador.agenciaResponsavel(2));
        assertEquals(0, particionador.agenciaResponsavel(3));
        assertEquals(1, particionador.agenciaResponsavel(4));
    }

    @Test
    @DisplayName("pertenceA responde se a conta e de uma agencia especifica")
    void verificaSeAContaPertenceAAgencia() {
        Particionador particionador = new Particionador(3);

        assertTrue(particionador.pertenceA(1, 1)); // conta 1 E da agencia 1
        assertFalse(particionador.pertenceA(1, 0)); // conta 1 NAO e da agencia 0
    }

    @Test
    @DisplayName("construtor rejeita total de agencias invalido")
    void rejeitaTotalInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Particionador(0));
        assertThrows(IllegalArgumentException.class, () -> new Particionador(-1));
    }

    @Test
    @DisplayName("rejeita id de conta negativo em vez de devolver agencia inexistente")
    void rejeitaIdNegativo() {
        Particionador particionador = new Particionador(3);

        assertThrows(IllegalArgumentException.class, () -> particionador.agenciaResponsavel(-1));
    }

    @Test
    @DisplayName("uma unica agencia e configuracao valida")
    void aceitaUmaUnicaAgencia() {
        Particionador particionador = new Particionador(1);

        assertEquals(0, particionador.agenciaResponsavel(0));
        assertEquals(0, particionador.agenciaResponsavel(7));
    }

}
