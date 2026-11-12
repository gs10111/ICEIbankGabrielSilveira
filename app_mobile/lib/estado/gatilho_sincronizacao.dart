import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';

import 'banco.dart';

class GatilhoDeSincronizacao {
  final Banco banco;
  final Stream<List<ConnectivityResult>> conectividade;
  final Duration intervalo;
  StreamSubscription<List<ConnectivityResult>>? _assinatura;
  Timer? _timer;

  GatilhoDeSincronizacao(
    this.banco, {
    required this.conectividade,
    this.intervalo = const Duration(seconds: 5),
  });

  void iniciar() {
    _assinatura = conectividade.listen((estado) {
      if (estado.any((c) => c != ConnectivityResult.none)) tentar();
    });
    _timer = Timer.periodic(intervalo, (_) {
      if (banco.temPendentes || banco.offline) tentar();
    });
  }

  Future<void> tentar() async {
    if (!banco.logado) return;
    try {
      await banco.sincronizarPendentes();
      if (banco.logado) {
        await banco.atualizar();
      }
    } catch (_) {}
  }

  void parar() {
    _assinatura?.cancel();
    _timer?.cancel();
  }
}
