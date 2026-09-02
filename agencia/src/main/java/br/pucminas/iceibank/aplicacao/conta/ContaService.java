package br.pucminas.iceibank.aplicacao.conta;

import br.pucminas.iceibank.aplicacao.porta.ContaRepositorio;
import br.pucminas.iceibank.aplicacao.porta.RegistroEventos;
import br.pucminas.iceibank.dominio.conta.Conta;
import br.pucminas.iceibank.dominio.particao.Particionador;
import br.pucminas.iceibank.dominio.relogio.Carimbo;
import br.pucminas.iceibank.dominio.relogio.RelogioLogico;

import java.math.BigDecimal;
import java.util.Map;

public class ContaService {

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final RelogioLogico relogio;
    private final RegistroEventos registro;

    public ContaService(int idAgencia,
            Particionador particionador,
            ContaRepositorio repositorio,
            RelogioLogico relogio,
            RegistroEventos registro) {
        this.idAgencia = idAgencia;
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.registro = registro;
    }

    public Conta abrir(int id, String nome, BigDecimal saldoInicial) {

        Carimbo carimbo = relogio.eventoLocal();
        Conta conta = new Conta(id, nome, saldoInicial);
        repositorio.salvar(conta);
        registro.registrar("CRIAR_CONTA", carimbo, Map.of("id", id, "nomeAluno", nome, "saldoInicial", saldoInicial));
        return conta;
    }
}
