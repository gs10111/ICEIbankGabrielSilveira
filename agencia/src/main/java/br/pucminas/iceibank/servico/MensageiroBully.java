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

/**
 * As tres conversas de rede do algoritmo de Bully. Nao decide nada: so leva a mensagem
 * e diz se a outra agencia respondeu.
 *
 * Tem o PROPRIO cliente HTTP, separado do da AgenciaRemota, porque o timeout aqui e o
 * coracao do algoritmo: "nao respondeu em 1 s" e lido como "esta fora do ar". O cliente
 * do extrato tolera 3 s porque la a pergunta e outra.
 *
 * Eleicao e coordenador vao com X-Agencia-Token: quem manda e um PROCESSO, como no
 * /interno. GET /bully/lider e publico, entao o heartbeat nao precisa de credencial.
 */
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

    /** true se a agencia de destino respondeu — o 200 e o "OK, estou viva" do Bully. */
    public boolean enviarEleicao(int destino, int origemId, CarimboVetorial vetorEnvio) {
        return postar(destino, "/bully/eleicao", Map.of("origemId", origemId, "vetorEnvio", vetorEnvio.valores()));
    }

    /** Fogo e esquece: quem estiver fora do ar descobre a lider quando voltar e eleger. */
    public void anunciarCoordenador(int destino, int liderId, CarimboVetorial vetorEnvio) {
        postar(destino, "/bully/coordenador", Map.of("liderId", liderId, "vetorEnvio", vetorEnvio.valores()));
    }

    /** O heartbeat: a agencia respondeu a uma leitura dentro do timeout? */
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
            return false;          // recusou, caiu ou demorou: para o Bully e tudo "nao respondeu"
        }
    }
}
