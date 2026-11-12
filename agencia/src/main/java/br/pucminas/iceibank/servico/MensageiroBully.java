package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.config.BullyProperties;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

@Component
public class MensageiroBully {
    private final AgenciaProperties agencias;
    private final String tokenEntreAgencias;
    private final RestClient http;

    public MensageiroBully(AgenciaProperties agencias, SegurancaProperties seguranca, BullyProperties bully) {
        this.agencias = agencias;
        this.tokenEntreAgencias = seguranca.tokenEntreAgencias();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(bully.timeoutMs()));
        fabrica.setReadTimeout(Duration.ofMillis(bully.timeoutMs()));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean enviarEleicao(int destino, int origemId, CarimboVetorial vetorEnvio) {
        return postar(destino, "/bully/eleicao", Map.of("origemId", origemId, "vetorEnvio", vetorEnvio.valores()));
    }

    public void anunciarCoordenador(int destino, int liderId, CarimboVetorial vetorEnvio) {
        postar(destino, "/bully/coordenador", Map.of("liderId", liderId, "vetorEnvio", vetorEnvio.valores()));
    }

    public boolean estaViva(int destino) {
        try {
            http.get().uri(agencias.urlDa(destino) + "/bully/lider").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private boolean postar(int destino, String caminho, Map<String, Object> corpo) {
        try {
            http.post().uri(agencias.urlDa(destino) + caminho)
                    .header("X-Agencia-Token", tokenEntreAgencias)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
