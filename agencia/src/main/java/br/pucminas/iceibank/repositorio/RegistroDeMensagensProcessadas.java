package br.pucminas.iceibank.repositorio;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegistroDeMensagensProcessadas {
    private final Set<String> jaVistas = ConcurrentHashMap.newKeySet();

    public boolean registrarSeInedita(String identidade) {
        return jaVistas.add(identidade);
    }

    public int quantidade() {
        return jaVistas.size();
    }
}
