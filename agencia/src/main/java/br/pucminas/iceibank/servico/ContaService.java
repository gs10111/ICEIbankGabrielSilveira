package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.RelogioLamport;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Regras de aplicacao das contas desta agencia.
 *
 * No MVC, o Controller so traduz HTTP; o Modelo (Conta) guarda a regra de negocio;
 * este Service e a cola: valida a particao, carimba o relogio e registra o evento.
 *
 * Toda operacao que ESCREVE segue a mesma sequencia:
 *   1. a conta e desta agencia?   2. a conta existe?
 *   3. carimba o relogio          4. o MODELO aplica a regra (Conta.depositar/sacar)
 *   5. salva                      6. registra o evento
 */
@Service
public class ContaService {

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioLamport relogio;
    private final RegistroDeEventos eventos;

    public ContaService(AgenciaProperties propriedades,
                        Particionador particionador,
                        ContaRepositorio repositorio,
                        RelogioLamport relogio,
                        RegistroDeEventos eventos) {
        this.idAgencia = propriedades.id();
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    public Conta abrir(int id, String nome, BigDecimal saldoInicial) {
        exigirQueSejaDestaAgencia(id);
        if (repositorio.existe(id)) {
            throw new ContaJaExisteException("conta ja existe nesta agencia: " + id);
        }

        // O carimbo vem DEPOIS das validacoes: so carimbamos o que de fato aconteceu.
        Carimbo carimbo = relogio.eventoLocal();

        Conta conta = new Conta(id, nome, saldoInicial);
        repositorio.salvar(conta);
        eventos.registrar("CRIAR_CONTA", carimbo,
                Map.of("id", id, "nomeAluno", nome, "saldoInicial", saldoInicial));
        return conta;
    }

    /** Leitura NAO carimba: nao altera estado e nao precisa ser ordenada causalmente. */
    public Conta consultar(int id) {
        exigirQueSejaDestaAgencia(id);
        return buscarOuFalhar(id);
    }

    public Conta depositar(int id, BigDecimal valor) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);

        Carimbo carimbo = relogio.eventoLocal();
        conta.depositar(valor);                  // a REGRA mora na Conta, nao aqui
        repositorio.salvar(conta);
        eventos.registrar("DEPOSITO", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    public Conta sacar(int id, BigDecimal valor) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);

        Carimbo carimbo = relogio.eventoLocal();
        conta.sacar(valor);
        repositorio.salvar(conta);
        eventos.registrar("SAQUE", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    /** FUNCIONALIDADE ADICIONAL 1: historico de eventos de uma conta. */
    public List<Evento> historico(int id, int limite) {
        if (limite <= 0) {
            throw new ValorInvalidoException("limite deve ser positivo: " + limite);
        }
        exigirQueSejaDestaAgencia(id);
        buscarOuFalhar(id);                      // 404 se a conta nao existe
        return eventos.ultimosDaConta(id, limite);
    }

    public int idAgencia() {
        return idAgencia;
    }

    public int quantidadeDeContas() {
        return repositorio.todas().size();
    }

    private void exigirQueSejaDestaAgencia(int id) {
        if (!particionador.pertenceA(id, idAgencia)) {
            throw new ContaNaoPertenceAgenciaException(
                    "conta " + id + " nao pertence a agencia " + idAgencia
                            + " (responsavel: agencia " + particionador.agenciaResponsavel(id) + ")");
        }
    }

    private Conta buscarOuFalhar(int id) {
        return repositorio.buscar(id)
                .orElseThrow(() -> new ContaNaoEncontradaException("conta nao encontrada nesta agencia: " + id));
    }
}
