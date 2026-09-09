package br.pucminas.iceibank;

import br.pucminas.iceibank.ferramentas.MesclarLogs;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IceiBankApplication {

    public static void main(String[] args) {
        // Atalho para a ferramenta da Parte E, sem subir o servidor:
        //   java -jar target/iceibank-agencia-1.0.0.jar --mesclar-logs [pasta]
        if (args.length > 0 && args[0].equals("--mesclar-logs")) {
            MesclarLogs.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(IceiBankApplication.class, args);
    }
}
