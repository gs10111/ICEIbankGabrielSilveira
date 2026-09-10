package br.pucminas.iceibank.controle;

import br.pucminas.iceibank.servico.AgenciaRemotaIndisponivelException;
import br.pucminas.iceibank.servico.ChaveIdempotenciaConflitanteException;
import br.pucminas.iceibank.modelo.conta.ContaJaExisteException;
import br.pucminas.iceibank.modelo.conta.ContaNaoEncontradaException;
import br.pucminas.iceibank.modelo.conta.ContaNaoPertenceAgenciaException;
import br.pucminas.iceibank.modelo.conta.SaldoInsuficienteException;
import br.pucminas.iceibank.modelo.conta.ValorInvalidoException;
import br.pucminas.iceibank.controle.dto.ErroResposta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * UNICO lugar do sistema que sabe traduzir excecao de dominio em codigo HTTP.
 *
 * Por que aqui e nao nas excecoes: se SaldoInsuficienteException carregasse
 * "400", o dominio estaria acoplado ao transporte. No Sprint 2, quando a entrada
 * virar mensagem de fila, "400" nao significa mais nada — mas a excecao continua
 * significando exatamente a mesma coisa.
 */
@RestControllerAdvice
public class ManipuladorDeErros {

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

    /**
     * A LIMITACAO CONHECIDA da Parte D chega aqui: 502, e o debito NAO foi revertido.
     * A mensagem diz isso explicitamente em vez de esconder a inconsistencia.
     */
    @ExceptionHandler(AgenciaRemotaIndisponivelException.class)
    public ResponseEntity<ErroResposta> agenciaIndisponivel(AgenciaRemotaIndisponivelException e) {
        return resposta(HttpStatus.BAD_GATEWAY,
                "Falha ao contatar agencia de destino. DEBITO JA APLICADO e nao revertido"
                        + " — inconsistencia conhecida, ver Sprint 4 (2PC/Saga). Causa: " + e.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaConflitanteException.class)
    public ResponseEntity<ErroResposta> chaveConflitante(ChaveIdempotenciaConflitanteException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Entrada fora de faixa (id de conta negativo no Particionador, por exemplo).
     * Sem este handler o Spring devolveria 500 — culpando o servidor por erro do cliente.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> argumentoInvalido(IllegalArgumentException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Erros das anotacoes de validacao (@NotNull, @Positive) nos DTOs de entrada. */
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
