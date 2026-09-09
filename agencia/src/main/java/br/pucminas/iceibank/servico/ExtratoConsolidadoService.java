package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.repositorio.ContaRepositorio;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.conta.Conta;
import br.pucminas.iceibank.modelo.particao.Particionador;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FUNCIONALIDADE ADICIONAL 3 — extrato consolidado.
 *
 * Soma os saldos de varias contas do mesmo aluno, mesmo espalhadas por agencias
 * diferentes. Para cada conta pedida decide, pela regra de particao, se a busca
 * e local ou remota.
 *
 * Este e o primeiro ponto do sistema que faz uma LEITURA distribuida, e ele
 * expoe um limite real: o total NAO e um snapshot atomico. As agencias sao
 * consultadas uma a uma, e uma transferencia pode acontecer entre duas leituras.
 * O resultado carrega `consistente` dizendo se alguma agencia falhou — melhor
 * devolver um numero rotulado como parcial do que um numero errado calado.
 */
@Service
public class ExtratoConsolidadoService {

    public record ItemDoExtrato(int id, String nomeAluno, BigDecimal saldo, int agencia, boolean disponivel) { }

    public record Extrato(BigDecimal total, List<ItemDoExtrato> contas, boolean consistente) { }

    private final int idAgencia;
    private final Particionador particionador;
    private final ContaRepositorio repositorio;
    private final AgenciaRemota consultaRemota;

    public ExtratoConsolidadoService(AgenciaProperties propriedades,
                                     Particionador particionador,
                                     ContaRepositorio repositorio,
                                     AgenciaRemota consultaRemota) {
        this.idAgencia = propriedades.id();
        this.particionador = particionador;
        this.repositorio = repositorio;
        this.consultaRemota = consultaRemota;
    }

    public Extrato consolidar(List<Integer> idsDeConta) {
        List<ItemDoExtrato> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean consistente = true;

        for (int id : idsDeConta.stream().distinct().toList()) {
            Optional<ItemDoExtrato> item = buscar(id);
            if (item.isEmpty()) {
                consistente = false;
                itens.add(new ItemDoExtrato(id, null, null, particionador.agenciaResponsavel(id), false));
                continue;
            }
            itens.add(item.get());
            total = total.add(item.get().saldo());
        }
        return new Extrato(total, List.copyOf(itens), consistente);
    }

    private Optional<ItemDoExtrato> buscar(int id) {
        int dona = particionador.agenciaResponsavel(id);

        if (dona == idAgencia) {
            return repositorio.buscar(id)
                    .map(conta -> new ItemDoExtrato(conta.id(), conta.nome(), conta.saldo(), idAgencia, true));
        }
        try {
            return consultaRemota.consultar(dona, id)
                    .map(remota -> new ItemDoExtrato(remota.id(), remota.nomeAluno(), remota.saldo(), dona, true));
        } catch (AgenciaRemotaIndisponivelException e) {
            return Optional.empty();     // agencia fora do ar: item marcado indisponivel
        }
    }

    /** Sobrecarga por titular: procura, entre as contas DESTA agencia, as do mesmo nome. */
    public List<Integer> idsDoTitularLocal(String nomeAluno) {
        return repositorio.todas().stream()
                .filter(conta -> conta.nome().equalsIgnoreCase(nomeAluno))
                .map(Conta::id)
                .toList();
    }
}
