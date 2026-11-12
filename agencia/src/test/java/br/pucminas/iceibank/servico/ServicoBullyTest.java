package br.pucminas.iceibank.servico;

import br.pucminas.iceibank.config.AgenciaProperties;
import br.pucminas.iceibank.config.BullyProperties;
import br.pucminas.iceibank.modelo.evento.Evento;
import br.pucminas.iceibank.modelo.relogio.CarimboVetorial;
import br.pucminas.iceibank.modelo.relogio.RelogioVetorial;
import br.pucminas.iceibank.repositorio.RegistroDeEventos;
import br.pucminas.iceibank.seguranca.SegurancaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicoBullyTest {
    private static final int TOTAL = 3;

    private static AgenciaProperties propriedades(int id) {
        return new AgenciaProperties(id, TOTAL,
                List.of("http://localhost:4016", "http://localhost:4017", "http://localhost:4018"),
                "target/dados-de-teste");
    }

    @Nested
    class Eleicao {
        @Test
        @DisplayName("sem ninguem de ID maior respondendo, a agencia se torna lider e anuncia as outras")
        void ninguemMaiorRespondeVira() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);

            zero.iniciarEleicao();

            assertThat(zero.liderAtual()).contains(0);
            assertThat(rede.registro(0).tipos()).containsExactly("ELEICAO_INICIADA", "TORNOU_LIDER");
            assertThat(rede.coordenadoresEnviados).containsExactlyInAnyOrder("0->1", "0->2");
        }

        @Test
        @DisplayName("se alguem de ID maior responde, a agencia NAO se declara lider: so espera o anuncio")
        void alguemMaiorRespondeEspera() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);
            rede.agenciaQueSoResponde(2);

            zero.iniciarEleicao();

            assertThat(zero.liderAtual()).isEmpty();
            assertThat(rede.registro(0).tipos()).containsExactly("ELEICAO_INICIADA");
        }

        @Test
        @DisplayName("a agencia de maior ID nao pergunta a ninguem: vence direto")
        void maiorIdVenceDireto() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully dois = rede.agencia(2);
            rede.agencia(0);
            rede.agencia(1);

            dois.iniciarEleicao();

            assertThat(rede.eleicoesEnviadas).isEmpty();
            assertThat(dois.liderAtual()).contains(2);
        }

        @Test
        @DisplayName("receber eleicao de ID menor registra o evento e dispara a propria eleicao")
        void receberEleicaoDisparaAPropria() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully um = rede.agencia(1);

            um.receberEleicao(0, new CarimboVetorial(List.of(3, 0, 0)));

            assertThat(rede.registro(1).tipos()).containsExactly("ELEICAO_RECEBIDA", "ELEICAO_INICIADA", "TORNOU_LIDER");
            assertThat(um.liderAtual()).contains(1);
        }

        @Test
        @DisplayName("mensagem recebida aplica a regra 3 do relogio vetorial (max e depois incremento)")
        void receberAplicaRegraDoVetor() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully um = rede.agencia(1);

            um.receberEleicao(0, new CarimboVetorial(List.of(5, 0, 0)));

            assertThat(rede.registro(1).eventos.get(0).carimbo().valores()).containsExactly(5, 1, 0);
        }

        @Test
        @DisplayName("anuncio de coordenador de ID maior e aceito")
        void aceitaCoordenadorMaior() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);

            zero.receberCoordenador(2, new CarimboVetorial(List.of(0, 0, 4)));

            assertThat(zero.liderAtual()).contains(2);
            assertThat(rede.registro(0).tipos()).containsExactly("LIDER_ANUNCIADO");
        }

        @Test
        @DisplayName("anuncio de coordenador de ID MENOR e recusado e convoca nova eleicao")
        void recusaCoordenadorMenor() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully dois = rede.agencia(2);
            rede.agencia(0);
            rede.agencia(1);

            dois.receberCoordenador(1, new CarimboVetorial(List.of(0, 3, 0)));

            assertThat(dois.liderAtual()).contains(2);
            assertThat(rede.registro(2).tipos())
                    .containsExactly("COORDENADOR_RECUSADO", "ELEICAO_INICIADA", "TORNOU_LIDER");
            assertThat(rede.agencia(1).liderAtual()).contains(2);
        }
    }

    @Nested
    class MensagemInvalida {
        @Test
        @DisplayName("coordenador com id fora da malha e recusado: nao vira lider uma agencia que nao existe")
        void coordenadorForaDaMalha() {
            ServicoBully zero = new RedeEmMemoria().agencia(0);

            assertThrows(IllegalArgumentException.class,
                    () -> zero.receberCoordenador(7, new CarimboVetorial(List.of(0, 0, 1))));
            assertThat(zero.liderAtual()).isEmpty();
        }

        @Test
        @DisplayName("eleicao com origem fora da malha e recusada")
        void eleicaoForaDaMalha() {
            ServicoBully zero = new RedeEmMemoria().agencia(0);

            assertThrows(IllegalArgumentException.class,
                    () -> zero.receberEleicao(-1, new CarimboVetorial(List.of(0, 0, 1))));
        }

        @Test
        @DisplayName("vetor com posicao negativa e recusado antes de tocar no relogio")
        void vetorNegativo() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);

            assertThrows(IllegalArgumentException.class,
                    () -> zero.receberCoordenador(2, new CarimboVetorial(List.of(0, -5, 1))));
            assertThat(rede.registro(0).eventos).isEmpty();
        }
    }

    @Nested
    class Convergencia {
        @Test
        @DisplayName("as 3 subindo juntas, cada uma disparando sua eleicao, convergem para a 2")
        void tresSobemEConvergemParaADois() {
            RedeEmMemoria rede = new RedeEmMemoria();
            List<ServicoBully> todas = List.of(rede.agencia(0), rede.agencia(1), rede.agencia(2));

            todas.forEach(ServicoBully::iniciarEleicao);

            assertThat(todas).allSatisfy(a -> assertThat(a.liderAtual()).contains(2));
        }

        @Test
        @DisplayName("sem a 2 no ar, 0 e 1 convergem para a 1")
        void semADoisConvergemParaAUm() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);
            ServicoBully um = rede.agencia(1);

            zero.iniciarEleicao();
            um.iniciarEleicao();

            assertThat(zero.liderAtual()).contains(1);
            assertThat(um.liderAtual()).contains(1);
        }

        @Test
        @DisplayName("a agencia de ID maior que volta depois retoma a lideranca")
        void maiorQueVoltaRetoma() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);
            ServicoBully um = rede.agencia(1);
            zero.iniciarEleicao();
            assertThat(zero.liderAtual()).contains(1);

            ServicoBully dois = rede.agencia(2);
            dois.iniciarEleicao();

            assertThat(List.of(zero, um, dois)).allSatisfy(a -> assertThat(a.liderAtual()).contains(2));
        }
    }

    @Nested
    class Heartbeat {
        @Test
        @DisplayName("sem lider conhecida, o heartbeat inicia uma eleicao")
        void semLiderElege() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);

            zero.verificarLider();

            assertThat(zero.liderAtual()).contains(0);
        }

        @Test
        @DisplayName("a propria lider nao verifica ninguem")
        void liderNaoSeVerifica() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully dois = rede.agencia(2);
            dois.iniciarEleicao();
            int eventosAntes = rede.registro(2).eventos.size();

            dois.verificarLider();

            assertThat(rede.registro(2).eventos).hasSize(eventosAntes);
            assertThat(rede.pings).isEmpty();
        }

        @Test
        @DisplayName("lider respondendo: nada muda e nada e registrado")
        void liderVivaNadaMuda() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);
            rede.agencia(2).iniciarEleicao();
            int eventosAntes = rede.registro(0).eventos.size();

            zero.verificarLider();

            assertThat(zero.liderAtual()).contains(2);
            assertThat(rede.pings).containsExactly("0->2");
            assertThat(rede.registro(0).eventos).hasSize(eventosAntes);
        }

        @Test
        @DisplayName("lider caiu: registra LIDER_NAO_RESPONDEU e as que sobraram elegem a 1")
        void liderCaiuReelege() {
            RedeEmMemoria rede = new RedeEmMemoria();
            ServicoBully zero = rede.agencia(0);
            ServicoBully um = rede.agencia(1);
            rede.agencia(2).iniciarEleicao();
            assertThat(zero.liderAtual()).contains(2);

            rede.derrubar(2);
            zero.verificarLider();

            assertThat(rede.registro(0).tipos()).contains("LIDER_NAO_RESPONDEU");
            assertThat(zero.liderAtual()).contains(1);
            assertThat(um.liderAtual()).contains(1);
        }
    }

    static class RedeEmMemoria {
        final ServicoBully[] nos = new ServicoBully[TOTAL];
        final RegistroEmLista[] registros = new RegistroEmLista[TOTAL];
        final Set<Integer> soRespondem = new HashSet<>();
        final List<String> eleicoesEnviadas = new ArrayList<>();
        final List<String> coordenadoresEnviados = new ArrayList<>();
        final List<String> pings = new ArrayList<>();

        ServicoBully agencia(int id) {
            if (nos[id] == null) {
                registros[id] = new RegistroEmLista(propriedades(id));
                nos[id] = new ServicoBully(propriedades(id), new RelogioVetorial(id, TOTAL), registros[id],
                        new Mensageiro(this, id), Runnable::run);
            }
            return nos[id];
        }

        void agenciaQueSoResponde(int id) {
            soRespondem.add(id);
        }

        void derrubar(int id) {
            nos[id] = null;
        }

        RegistroEmLista registro(int id) {
            return registros[id];
        }

        boolean viva(int id) {
            return nos[id] != null || soRespondem.contains(id);
        }
    }

    static class Mensageiro extends MensageiroBully {
        private final RedeEmMemoria rede;
        private final int dono;

        Mensageiro(RedeEmMemoria rede, int dono) {
            super(propriedades(dono), new SegurancaProperties("x".repeat(32), 900, "t"),
                    new BullyProperties(200, 3000, false));
            this.rede = rede;
            this.dono = dono;
        }

        @Override
        public boolean enviarEleicao(int destino, int origemId, CarimboVetorial vetorEnvio) {
            rede.eleicoesEnviadas.add(dono + "->" + destino);
            if (!rede.viva(destino)) return false;
            if (rede.nos[destino] != null) rede.nos[destino].receberEleicao(origemId, vetorEnvio);
            return true;
        }

        @Override
        public void anunciarCoordenador(int destino, int liderId, CarimboVetorial vetorEnvio) {
            rede.coordenadoresEnviados.add(dono + "->" + destino);
            if (rede.nos[destino] != null) rede.nos[destino].receberCoordenador(liderId, vetorEnvio);
        }

        @Override
        public boolean estaViva(int destino) {
            rede.pings.add(dono + "->" + destino);
            return rede.viva(destino);
        }
    }

    static class RegistroEmLista extends RegistroDeEventos {
        final List<Evento> eventos = new ArrayList<>();

        RegistroEmLista(AgenciaProperties propriedades) {
            super(propriedades);
        }

        @Override
        public Evento registrar(String tipo, CarimboVetorial carimbo, Map<String, Object> detalhes) {
            Evento evento = new Evento("teste", tipo, carimbo, Instant.now(), detalhes);
            eventos.add(evento);
            return evento;
        }

        List<String> tipos() {
            return eventos.stream().map(Evento::tipo).toList();
        }
    }
}
