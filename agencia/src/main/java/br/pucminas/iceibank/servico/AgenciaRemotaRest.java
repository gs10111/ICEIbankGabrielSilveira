package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.servico.AgenciaRemota;
import br.pucminas.iceibank.servico.AgenciaRemotaIndisponivelException;
import br.pucminas.iceibank.servico.ConsultaContaRemota;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.CarimboLamport;
import br.pucminas.iceibank.config.AgenciaProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.Optional;

/**
 * Adapter de saida: fala com outra agencia por REST.
 *
 * TODO Sprint 2: trocar por AgenciaRemotaFila (pub/sub). Nenhuma classe de
 * aplicacao ou dominio muda — so o @Bean em BeansDaAgencia.
 */
public class AgenciaRemotaRest implements AgenciaRemota, ConsultaContaRemota {

    private final AgenciaProperties propriedades;
    private final RestClient http;
    private final String tokenEntreAgencias;

    public AgenciaRemotaRest(AgenciaProperties propriedades, RestClient http, String tokenEntreAgencias) {
        this.propriedades = propriedades;
        this.http = http;
        this.tokenEntreAgencias = tokenEntreAgencias;
    }

    @Override
    public void creditar(int idAgenciaDestino, int idConta, BigDecimal valor,
                         Carimbo carimbo, int agenciaOrigem) {
        String url = propriedades.urlDa(idAgenciaDestino) + "/contas/" + idConta + "/creditar-remoto";
        try {
            http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Agencia-Token", tokenEntreAgencias)
                    .body(Map.of(
                            "valor", valor,
                            "timestampLamport", valorDo(carimbo),
                            "origemAgencia", agenciaOrigem))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AgenciaRemotaIndisponivelException(
                    "falha ao contatar agencia " + idAgenciaDestino + " em " + url + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ContaRemota> consultar(int idAgencia, int idConta) {
        String url = propriedades.urlDa(idAgencia) + "/contas/" + idConta;
        try {
            return Optional.ofNullable(http.get().uri(url)
                    .header("X-Agencia-Token", tokenEntreAgencias)
                    .retrieve().body(ContaRemota.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();          // conta nao existe la: nao e indisponibilidade
        } catch (RestClientException e) {
            throw new AgenciaRemotaIndisponivelException(
                    "falha ao consultar agencia " + idAgencia + " em " + url + ": " + e.getMessage(), e);
        }
    }

    private static int valorDo(Carimbo carimbo) {
        return switch (carimbo) {
            case CarimboLamport(int valor) -> valor;
        };
    }
}
