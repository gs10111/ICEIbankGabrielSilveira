package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.AgenciaRemotaIndisponivelException;
import br.pucminas.iceibank.servico.BrokerIndisponivelException;
import br.pucminas.iceibank.servico.ChaveIdempotenciaConflitanteException;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.controle.dto.ConflitoResposta;
import br.pucminas.iceibank.controle.dto.ContaResposta;
import br.pucminas.iceibank.controle.dto.ErroResposta;
import br.pucminas.iceibank.servico.ConflitoDeSincronizacaoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ManipuladorDeErros {
    private final int idAgencia;

    public ManipuladorDeErros(AgenciaProperties propriedades) {
        this.idAgencia = propriedades.id();
    }

    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ResponseEntity<ErroResposta> naoEncontrada(ContaNaoEncontradaException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ContaJaExisteException.class)
    public ResponseEntity<ErroResposta> jaExiste(ContaJaExisteException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({ContaNaoPertenceAgenciaException.class,
                       SaldoInsuficienteException.class,
                       ValorInvalidoException.class})
    public ResponseEntity<ErroResposta> requisicaoInvalida(RuntimeException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AgenciaRemotaIndisponivelException.class)
    public ResponseEntity<ErroResposta> agenciaIndisponivel(AgenciaRemotaIndisponivelException e) {
        return resposta(HttpStatus.BAD_GATEWAY,
                "Falha ao contatar agencia de destino. DEBITO JA APLICADO e nao revertido"
                        + " — inconsistencia conhecida, ver Sprint 4 (2PC/Saga). Causa: " + e.getMessage());
    }

    @ExceptionHandler(BrokerIndisponivelException.class)
    public ResponseEntity<ErroResposta> brokerIndisponivel(BrokerIndisponivelException e) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE,
                "Mensageria indisponivel: a transferencia NAO foi publicada e o DEBITO JA FOI"
                        + " APLICADO — inconsistencia conhecida, ver Sprint 4 (2PC/Saga). Causa: "
                        + e.getMessage());
    }

    @ExceptionHandler(ConflitoDeSincronizacaoException.class)
    public ResponseEntity<ConflitoResposta> conflitoDeSincronizacao(ConflitoDeSincronizacaoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflitoResposta(
                e.getMessage(), e.vetorConhecido().valores(), e.vetorAtual().valores(),
                ContaResposta.de(e.contaAtual(), idAgencia, e.vetorAtual())));
    }

    @ExceptionHandler(ChaveIdempotenciaConflitanteException.class)
    public ResponseEntity<ErroResposta> chaveConflitante(ChaveIdempotenciaConflitanteException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> argumentoInvalido(IllegalArgumentException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> corpoInvalido(MethodArgumentNotValidException e) {
        String detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return resposta(HttpStatus.BAD_REQUEST, detalhes);
    }

    private ResponseEntity<ErroResposta> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResposta(mensagem));
    }
}
