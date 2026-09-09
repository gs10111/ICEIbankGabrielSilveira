package br.pucminas.iceibank.infra.config;

import br.pucminas.iceibank.aplicacao.conta.ContaService;
import br.pucminas.iceibank.dominio.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.infra.seguranca.RepositorioDeCredenciais;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cria as contas de demonstracao no boot.
 *
 * Existe por um motivo concreto: POST /contas exige JWT (o roteiro manda proteger
 * "criar conta"), mas para obter um JWT e preciso ter conta e senha. Sem uma carga
 * inicial, o sistema nasce inacessivel.
 *
 * Cada agencia tenta criar TODAS as contas da lista e o Particionador recusa as que
 * nao sao dela — a propria regra de particao faz a distribuicao, sem nenhuma
 * configuracao por agencia. Contas 0 e 3 tem a mesma titular de proposito: e o que
 * torna o extrato consolidado (funcionalidade adicional 3) demonstravel, ja que
 * 0 % 3 = 0 e 3 % 3 = 0... e 1 e 4 ficam nas agencias 1 e 2.
 */
@Component
public class CarregadorDeContasIniciais implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CarregadorDeContasIniciais.class);

    /** id, titular, saldo inicial, senha. */
    private record ContaInicial(int id, String nome, String saldo, String senha) { }

    private static final ContaInicial[] DEMONSTRACAO = {
            new ContaInicial(0, "Ana Souza",   "1000.00", "ana123"),    // 0 % 3 = 0 -> agencia 0
            new ContaInicial(1, "Bruno Lima",   "800.00", "bruno123"),  // 1 % 3 = 1 -> agencia 1
            new ContaInicial(2, "Carla Dias",   "500.00", "carla123"),  // 2 % 3 = 2 -> agencia 2
            new ContaInicial(3, "Diego Melo",   "300.00", "diego123"),  // 3 % 3 = 0 -> agencia 0
            new ContaInicial(4, "Ana Souza",    "250.00", "ana123"),    // 4 % 3 = 1 -> agencia 1
            new ContaInicial(5, "Bruno Lima",   "120.00", "bruno123"),  // 5 % 3 = 2 -> agencia 2
    };

    private final ContaService contaService;
    private final RepositorioDeCredenciais credenciais;
    private final AgenciaProperties propriedades;

    public CarregadorDeContasIniciais(ContaService contaService,
                                      RepositorioDeCredenciais credenciais,
                                      AgenciaProperties propriedades) {
        this.contaService = contaService;
        this.credenciais = credenciais;
        this.propriedades = propriedades;
    }

    @Override
    public void run(ApplicationArguments args) {
        int criadas = 0;
        for (ContaInicial conta : DEMONSTRACAO) {
            try {
                contaService.abrir(conta.id(), conta.nome(), new BigDecimal(conta.saldo()));
                credenciais.cadastrar(conta.id(), conta.senha());
                criadas++;
            } catch (ContaNaoPertenceAgenciaException ignorada) {
                // Esperado: a conta e de outra agencia. A regra de particao decide.
            }
        }
        log.info("Agencia {} iniciada com {} conta(s) de demonstracao (particao: id % {} == {})",
                propriedades.id(), criadas, propriedades.total(), propriedades.id());
    }
}
