package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.modelo.relogio.Carimbo;
import br.pucminas.iceibank.modelo.relogio.CarimboLamport;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente REST das OUTRAS agencias. E o unico ponto do backend que faz uma
 * chamada de saida pela rede.
 *
 * Autentica com X-Agencia-Token, nao com o JWT de quem esta logado: um JWT
 * identifica uma PESSOA, e quem chama /creditar-remoto e um PROCESSO. Repassar o
 * token do usuario daria a esta agencia o poder de agir como ele em qualquer
 * outra — o problema do "confused deputy".
 */
@Component
public class AgenciaRemota {

    /** Resposta de GET /contas/{id} em outra agencia. */
    public record ContaRemota(int id, String nomeAluno, BigDecimal saldo, int agencia) { }

    private final AgenciaProperties propriedades;
    private final RestClient http;
    private final String tokenEntreAgencias;

    public AgenciaRemota(AgenciaProperties propriedades, RestClient http, SegurancaProperties seguranca) {
        this.propriedades = propriedades;
        this.http = http;
        this.tokenEntreAgencias = seguranca.tokenEntreAgencias();
    }

    /** Credita numa conta de outra agencia, levando o carimbo de Lamport na mensagem. */
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

    /** Vazio se a conta nao existe la; excecao se a agencia nao respondeu. */
    public Optional<ContaRemota> consultar(int idAgencia, int idConta) {
        String url = propriedades.urlDa(idAgencia) + "/contas/" + idConta + "/interno";
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
