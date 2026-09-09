package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.servico.ConsultaEventos;
import br.pucminas.iceibank.servico.ContaRepositorio;
import br.pucminas.iceibank.servico.RegistroEventos;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.RelogioLogico;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Casos de uso de conta desta agencia.
 *
 * Depende SO de abstracoes (as portas) — por isso e testavel sem Spring,
 * sem HTTP e sem disco. E o D do SOLID (Dependency Inversion) na pratica.
 */
public class ContaService {

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioLogico relogio;
    private final RegistroEventos registro;
    private final ConsultaEventos consultaEventos;

    public ContaService(int idAgencia,
                        Particionador particionador,
                        ContaRepositorio repositorio,
                        RelogioLogico relogio,
                        RegistroEventos registro,
                        ConsultaEventos consultaEventos) {
        this.idAgencia = idAgencia;
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.registro = registro;
        this.consultaEventos = consultaEventos;
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
        registro.registrar("CRIAR_CONTA", carimbo,
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
        registro.registrar("DEPOSITO", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    public Conta sacar(int id, BigDecimal valor) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);

        Carimbo carimbo = relogio.eventoLocal();
        conta.sacar(valor);
        repositorio.salvar(conta);
        registro.registrar("SAQUE", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    /** FUNCIONALIDADE ADICIONAL 1: historico de eventos de uma conta. */
    public List<Evento> historico(int id, int limite) {
        exigirQueSejaDestaAgencia(id);
        buscarOuFalhar(id);                      // 404 se a conta nao existe
        return consultaEventos.ultimosDaConta(id, limite);
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
