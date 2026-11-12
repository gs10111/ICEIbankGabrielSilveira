import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';

import 'banco.dart';

/// SPRINT 3, Parte F — dispara a sincronizacao sozinho, sem a pessoa apertar nada.
///
/// Dois gatilhos, porque um so nao basta:
///  1. connectivity_plus avisa quando a rede VOLTA (saiu do modo aviao, Wi-Fi religou).
///     E o caminho rapido: sincroniza no mesmo instante.
///  2. um timer de 5 s enquanto houver pendente ou o app estiver offline. O plugin so
///     sabe se o aparelho tem rede, nao se a AGENCIA responde: com Wi-Fi ligado e a
///     agencia derrubada nao chega evento nenhum quando ela volta.
class GatilhoDeSincronizacao {
  final Banco banco;
  final Stream<List<ConnectivityResult>> conectividade;
  final Duration intervalo;
  StreamSubscription<List<ConnectivityResult>>? _assinatura;
  Timer? _timer;

  GatilhoDeSincronizacao(this.banco, {required this.conectividade, this.intervalo = const Duration(seconds: 5)});

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
      if (banco.logado) await banco.atualizar();      // traz o saldo confirmado e limpa o "offline"
    } catch (_) {
      // erro de API ja virou estado no Banco (sessao expirada, conflito); nada a fazer aqui
    }
  }

  void parar() {
    _assinatura?.cancel();
    _timer?.cancel();
  }
}
