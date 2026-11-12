package br.pucminas.iceibank.config;

import br.pucminas.iceibank.servico.ContaService;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.seguranca.RepositorioDeCredenciais;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CarregadorDeContasIniciais implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CarregadorDeContasIniciais.class);

    private record ContaInicial(int id, String nome, String saldo, String senha) { }

    private static final ContaInicial[] DEMONSTRACAO = {
            new ContaInicial(0, "Ana Souza",   "1000.00", "ana123"),
            new ContaInicial(1, "Bruno Lima",   "800.00", "bruno123"),
            new ContaInicial(2, "Carla Dias",   "500.00", "carla123"),
            new ContaInicial(3, "Diego Melo",   "300.00", "diego123"),
            new ContaInicial(4, "Ana Souza",    "250.00", "ana123"),
            new ContaInicial(5, "Bruno Lima",   "120.00", "bruno123"),
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
            }
        }
        log.info("Agencia {} iniciada com {} conta(s) de demonstracao (particao: id % {} == {})",
                propriedades.id(), criadas, propriedades.total(), propriedades.id());
    }
}
