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
        repositorio.inserir(new Conta(3, "Ana", new BigDecimal("100.00")));

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
        repositorio.inserir(new Conta(3, "Ana", new BigDecimal("100.00")));

        assertThat(repositorio.existe(3)).isTrue();
        assertThat(repositorio.existe(99)).isFalse();
    }

    @Test
    @DisplayName("todas devolve as contas cadastradas")
    void todasDevolveAsContas() {
        repositorio.inserir(new Conta(0, "Ana", new BigDecimal("10.00")));
        repositorio.inserir(new Conta(3, "Bia", new BigDecimal("20.00")));

        assertThat(repositorio.todas()).hasSize(2);
    }

    @Test
    @DisplayName("alterar a conta buscada ja altera a que esta no repositorio")
    void mutacaoEhVisivelSemNenhumaChamadaAMais() {
        // E por isto que "salvar depois de alterar" nao existe: o mapa guarda a
        // REFERENCIA. Os seis repositorio.salvar(...) que havia nos servicos eram
        // put() do objeto por ele mesmo. Este teste trava esse contrato — se um dia
        // o repositorio passar a guardar copia, ele quebra e avisa.
        repositorio.inserir(new Conta(3, "Ana", new BigDecimal("100.00")));

        repositorio.buscar(3).orElseThrow().depositar(new BigDecimal("50.00"));

        assertThat(repositorio.buscar(3).orElseThrow().saldo()).isEqualByComparingTo("150.00");
    }
}
