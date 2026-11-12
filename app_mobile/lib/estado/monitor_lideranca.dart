import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/api_cliente.dart';

class MonitorLideranca extends ChangeNotifier {
  final ApiCliente api;
  final Duration intervalo;
  Timer? _timer;
  bool _consultando = false;
  int _preferida = 0;

  int? lider;
  int? consultadaEm;

  Map<int, bool> ativas = const {};

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
    if (_consultando) return;
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
          continue;
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
