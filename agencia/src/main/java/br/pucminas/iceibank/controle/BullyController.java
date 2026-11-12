package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.controle.dto.MensagemBully;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.servico.ServicoBully;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPRINT 3, Parte A — as rotas do Bully. So traduz HTTP; o algoritmo esta no ServicoBully.
 *
 * Quem pode chamar (ver FiltroJwt):
 *   POST /bully/eleicao, /bully/coordenador   so outra agencia, com X-Agencia-Token
 *   GET  /bully/lider                         publico — heartbeat e app mobile
 */
@RestController
@RequestMapping("/bully")
public class BullyController {

    private final ServicoBully bully;

    public BullyController(ServicoBully bully) {
        this.bully = bully;
    }

    /** O 200 desta rota E a resposta "estou viva" do algoritmo. */
    @PostMapping("/eleicao")
    public void eleicao(@Valid @RequestBody MensagemBully.Eleicao mensagem) {
        bully.receberEleicao(mensagem.origemId(), new CarimboVetorial(mensagem.vetorEnvio()));
    }

    @PostMapping("/coordenador")
    public void coordenador(@Valid @RequestBody MensagemBully.Coordenador mensagem) {
        bully.receberCoordenador(mensagem.liderId(), new CarimboVetorial(mensagem.vetorEnvio()));
    }

    @GetMapping("/lider")
    public MensagemBully.Lider lider() {
        return new MensagemBully.Lider(bully.idAgencia(), bully.liderAtual().orElse(null));
    }
}
