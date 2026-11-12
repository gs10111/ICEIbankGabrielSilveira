package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class AgenciaRemota {
    public record ContaRemota(int id, String nomeAluno, BigDecimal saldo, int agencia) { }

    private final AgenciaProperties propriedades;
    private final RestClient http;
    private final String tokenEntreAgencias;

    public AgenciaRemota(AgenciaProperties propriedades, RestClient http, SegurancaProperties seguranca) {
        this.propriedades = propriedades;
        this.http = http;
        this.tokenEntreAgencias = seguranca.tokenEntreAgencias();
    }

    public Optional<ContaRemota> consultar(int idAgencia, int idConta) {
        String url = propriedades.urlDa(idAgencia) + "/contas/" + idConta + "/interno";
        try {
            return Optional.ofNullable(http.get().uri(url)
                    .header("X-Agencia-Token", tokenEntreAgencias)
                    .retrieve().body(ContaRemota.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new AgenciaRemotaIndisponivelException(
                    "falha ao consultar agencia " + idAgencia + " em " + url + ": " + e.getMessage(), e);
        }
    }
}
