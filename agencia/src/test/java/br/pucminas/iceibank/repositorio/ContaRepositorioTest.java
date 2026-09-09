package br.pucminas.iceibank.repositorio;

import br.pucminas.iceibank.modelo.conta.Conta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContaRepositorioTest {

    private ContaRepositorio repositorio;

    @BeforeEach
    void criarRepositorioVazio() {
        repositorio = new ContaRepositorio();
    }

    @Test
    @DisplayName("conta salva pode ser buscada pelo id")
    void salvaEBusca() {
        repositorio.salvar(new Conta(3, "Ana", new BigDecimal("100.00")));

        Optional<Conta> encontrada = repositorio.buscar(3);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().nome()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("buscar conta inexistente devolve Optional vazio, nunca null")
    void buscaInexistenteDevolveVazio() {
        assertThat(repositorio.buscar(99)).isEmpty();
    }

    @Test
    @DisplayName("existe responde se a conta ja esta cadastrada")
    void existeResponde() {
        repositorio.salvar(new Conta(3, "Ana", new BigDecimal("100.00")));

        assertThat(repositorio.existe(3)).isTrue();
        assertThat(repositorio.existe(99)).isFalse();
    }

    @Test
    @DisplayName("todas devolve as contas cadastradas")
    void todasDevolveAsContas() {
        repositorio.salvar(new Conta(0, "Ana", new BigDecimal("10.00")));
        repositorio.salvar(new Conta(3, "Bia", new BigDecimal("20.00")));

        assertThat(repositorio.todas()).hasSize(2);
    }
}
