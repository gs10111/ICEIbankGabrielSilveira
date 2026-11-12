import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/api_cliente.dart';

/// SPRINT 3, Parte D — de tempos em tempos pergunta GET /bully/lider e avisa a tela.
///
/// INTERVALO: 3 s, o mesmo do heartbeat das agencias. Consultar mais rapido nao
/// adianta: a agencia so descobre a queda no proximo heartbeat dela, entao o app
/// veria a mesma resposta velha varias vezes, gastando bateria e rede a toa. Mais
/// devagar, a troca demora mais para aparecer (pergunta 8.3.1).
///
/// FALLBACK: comeca pela ultima agencia que respondeu e, se ela nao responder, usa
/// as outras em ordem antes de desistir. Quando a lider cai, a agencia consultada
/// pode ser justamente ela — e o painel precisa continuar funcionando.
///
/// FUNCIONALIDADE ADICIONAL (Sprint 3, 2.1) — agencias ativas: as 3 sao consultadas
/// EM PARALELO a cada ciclo. Sai de graca o mapa de quem responde, e o fallback nao
/// soma timeouts: com a 0 fora, a resposta da 1 ja esta pronta quando o timeout da 0
/// estoura. O custo e 3 requisicoes por ciclo em vez de 1 (pergunta 8.3.1).
///
/// DIVERGENCIA: logo depois de uma queda, uma agencia pode ainda nao saber da nova
/// eleicao. O painel mostra a resposta da agencia preferida e avisa que ha
/// discordancia; na consulta seguinte ela some sozinha (pergunta 8.3.2).
class MonitorLideranca extends ChangeNotifier {
  final ApiCliente api;
  final Duration intervalo;
  Timer? _timer;
  bool _consultando = false;
  int _preferida = 0;

  int? lider;
  int? consultadaEm;

  /// id da agencia -> respondeu no ultimo ciclo?
  Map<int, bool> ativas = const {};

  /// As agencias que responderam nao concordam sobre quem e a lider.
  bool divergencia = false;
  DateTime? atualizadoEm;
  bool nenhumaRespondeu = false;

  MonitorLideranca(this.api, {this.intervalo = const Duration(seconds: 3)});

  bool get emEleicao =>
      !nenhumaRespondeu && consultadaEm != null && lider == null;

  void iniciar() {
    _timer?.cancel();
    consultar();
    _timer = Timer.periodic(intervalo, (_) => consultar());
  }

  void parar() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> consultar() async {
    if (_consultando) return; // uma consulta lenta nao empilha com a proxima
    _consultando = true;
    try {
      final agencias = api.malha.agencias.toList();
      final respostas = await Future.wait(agencias.map(_perguntar));
      final porAgencia = {
        for (var i = 0; i < agencias.length; i++) agencias[i]: respostas[i],
      };

      ativas = {for (final a in agencias) a: porAgencia[a]!.respondeu};
      atualizadoEm = DateTime.now();
      final total = agencias.length;
      for (var passo = 0; passo < total; passo++) {
        final agencia = (_preferida + passo) % total;
        if (!porAgencia[agencia]!.respondeu) {
          continue; // fora do ar: usa a proxima
        }
        lider = porAgencia[agencia]!.lider;
        _preferida = agencia;
        consultadaEm = agencia;
        nenhumaRespondeu = false;
        divergencia =
            porAgencia.values
                .where((r) => r.respondeu)
                .map((r) => r.lider)
                .toSet()
                .length >
            1;
        notifyListeners();
        return;
      }
      nenhumaRespondeu = true;
      divergencia = false;
      notifyListeners();
    } finally {
      _consultando = false;
    }
  }

  Future<({bool respondeu, int? lider})> _perguntar(int agencia) async {
    try {
      return (respondeu: true, lider: await api.liderSegundo(agencia));
    } on SemConexao {
      return (respondeu: false, lider: null);
    } on ErroApi {
      return (respondeu: false, lider: null);
    }
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
