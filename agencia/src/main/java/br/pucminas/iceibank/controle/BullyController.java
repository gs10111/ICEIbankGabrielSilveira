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

@RestController
@RequestMapping("/bully")
public class BullyController {
    private final ServicoBully bully;

    public BullyController(ServicoBully bully) {
        this.bully = bully;
    }

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
