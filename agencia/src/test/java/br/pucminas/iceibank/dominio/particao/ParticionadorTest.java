package br.pucminas.iceibank.dominio.particao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
