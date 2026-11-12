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
/// FALLBACK: comeca pela ultima agencia que respondeu e, se ela nao responder, tenta
/// as outras em ordem antes de desistir. Quando a lider cai, a agencia consultada
/// pode ser justamente ela — e o painel precisa continuar funcionando.
class MonitorLideranca extends ChangeNotifier {
  final ApiCliente api;
  final Duration intervalo;
  Timer? _timer;
  bool _consultando = false;
  int _preferida = 0;

  int? lider;
  int? consultadaEm;
  DateTime? atualizadoEm;
  bool nenhumaRespondeu = false;

  MonitorLideranca(this.api, {this.intervalo = const Duration(seconds: 3)});

  bool get emEleicao => !nenhumaRespondeu && consultadaEm != null && lider == null;

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
    if (_consultando) return;            // uma consulta lenta nao empilha com a proxima
    _consultando = true;
    try {
      final total = api.malha.total;
      for (var passo = 0; passo < total; passo++) {
        final agencia = (_preferida + passo) % total;
        try {
          lider = await api.liderSegundo(agencia);
          _preferida = agencia;
          consultadaEm = agencia;
          nenhumaRespondeu = false;
          atualizadoEm = DateTime.now();
          notifyListeners();
          return;
        } on SemConexao {
          continue;                       // fora do ar: tenta a proxima
        } on ErroApi {
          continue;
        }
      }
      nenhumaRespondeu = true;
      atualizadoEm = DateTime.now();
      notifyListeners();
    } finally {
      _consultando = false;
    }
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
