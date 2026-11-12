package br.pucminas.iceibank.seguranca;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RepositorioDeCredenciais {
    private final Map<Integer, Credencial> porConta = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder cifrador = new BCryptPasswordEncoder();

    public void cadastrar(int idConta, String senhaEmTextoPuro) {
        porConta.put(idConta, new Credencial(idConta, cifrador.encode(senhaEmTextoPuro)));
    }

    public boolean senhaConfere(int idConta, String senhaEmTextoPuro) {
        Optional<Credencial> credencial = Optional.ofNullable(porConta.get(idConta));
        return credencial.filter(c -> cifrador.matches(senhaEmTextoPuro, c.senhaCifrada())).isPresent();
    }

    public boolean temCredencial(int idConta) {
        return porConta.containsKey(idConta);
    }
}
