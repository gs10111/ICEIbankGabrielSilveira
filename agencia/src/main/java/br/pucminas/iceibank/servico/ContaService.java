package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.particao.Particionador;
import br.pucminas.iceibank.modelo.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.Relacao;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ContaService {
    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioVetorial relogio;
    private final RegistroDeEventos eventos;

    public ContaService(AgenciaProperties propriedades,
                        Particionador particionador,
                        ContaRepositorio repositorio,
                        RelogioVetorial relogio,
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

        CarimboVetorial carimbo = relogio.eventoLocal();

        Conta conta = new Conta(id, nome, saldoInicial);
        repositorio.inserir(conta);
        eventos.registrar("CRIAR_CONTA", carimbo,
                Map.of("id", id, "nomeAluno", nome, "saldoInicial", saldoInicial));
        return conta;
    }

    public Conta consultar(int id) {
        exigirQueSejaDestaAgencia(id);
        return buscarOuFalhar(id);
    }

    public Conta depositar(int id, BigDecimal valor) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);

        CarimboVetorial carimbo = relogio.eventoLocal();
        conta.depositar(valor);
        eventos.registrar("DEPOSITO", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    public Conta sacar(int id, BigDecimal valor) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);

        CarimboVetorial carimbo = relogio.eventoLocal();
        conta.sacar(valor);
        eventos.registrar("SAQUE", carimbo,
                Map.of("id", id, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    public CarimboVetorial vetorAtual() {
        return relogio.valorAtual();
    }

    public Conta sincronizar(int id, String tipo, BigDecimal valor, CarimboVetorial vetorConhecido) {
        exigirQueSejaDestaAgencia(id);
        Conta conta = buscarOuFalhar(id);
        if (!"DEPOSITO".equals(tipo) && !"SAQUE".equals(tipo)) {
            throw new ValorInvalidoException("tipo de operacao pendente nao suportado: " + tipo
                    + " (aceitos: DEPOSITO, SAQUE)");
        }

        CarimboVetorial agora = relogio.valorAtual();
        if (CarimboVetorial.comparar(vetorConhecido, agora) != Relacao.IGUAIS) {
            throw new ConflitoDeSincronizacaoException(vetorConhecido, agora, conta);
        }
        if (valor == null || valor.signum() <= 0) {
            throw new ValorInvalidoException("valor deve ser positivo: " + valor);
        }
        if ("SAQUE".equals(tipo) && conta.saldo().compareTo(valor) < 0) {
            throw new SaldoInsuficienteException("saldo insuficiente: saldo=" + conta.saldo() + " valor=" + valor);
        }

        CarimboVetorial carimbo = relogio.eventoLocalSeAindaFor(vetorConhecido)
                .orElseThrow(() -> new ConflitoDeSincronizacaoException(vetorConhecido, relogio.valorAtual(), conta));
        if ("DEPOSITO".equals(tipo)) {
            conta.depositar(valor);
        } else {
            conta.sacar(valor);
        }
        eventos.registrar("SINCRONIZACAO_OFFLINE", carimbo,
                Map.of("id", id, "tipo", tipo, "valor", valor, "novoSaldo", conta.saldo()));
        return conta;
    }

    public List<Evento> historico(int id, int limite) {
        if (limite <= 0) {
            throw new ValorInvalidoException("limite deve ser positivo: " + limite);
        }
        exigirQueSejaDestaAgencia(id);
        buscarOuFalhar(id);
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
