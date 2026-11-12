import 'dart:math';

import 'package:flutter/foundation.dart';

import '../api/api_cliente.dart';
import '../modelo/conta.dart';
import '../modelo/dinheiro.dart';

/// Estado do fluxo bancario: sessao, ultima leitura da conta e as operacoes.
///
/// ChangeNotifier puro, sem pacote de gerencia de estado: sao duas telas e um
/// punhado de campos. As telas escutam com ListenableBuilder.
///
/// As operacoes LANCAM a excecao (ErroApi / SemConexao) para a tela mostrar a
/// mensagem do backend; o estado so guarda o que precisa sobreviver entre telas.
class Banco extends ChangeNotifier {
  final ApiCliente api;

  Sessao? sessao;
  ContaLida? conta;

  /// Motivo de a sessao ter acabado sozinha (401), para a tela de login explicar.
  String? avisoDeSessao;

  Banco(this.api);

  bool get logado => sessao != null;

  Future<void> entrar(int idConta, String senha) async {
    final nova = await api.login(idConta, senha);
    api.token = nova.token;
    sessao = nova;
    avisoDeSessao = null;
    notifyListeners();
    await atualizar();
  }

  Future<void> atualizar() => _protegido(() async {
        conta = await api.consultar(sessao!.idConta);
        notifyListeners();
      });

  Future<String> depositar(int centavos) => _protegido(() async {
        conta = await api.depositar(sessao!.idConta, centavos);
        notifyListeners();
        return 'Depósito de ${formatarReais(centavos)} aplicado.';
      });

  Future<String> sacar(int centavos) => _protegido(() async {
        conta = await api.sacar(sessao!.idConta, centavos);
        notifyListeners();
        return 'Saque de ${formatarReais(centavos)} aplicado.';
      });

  Future<String> transferir(int idDestino, int centavos) => _protegido(() async {
        final recibo = await api.transferir(sessao!.idConta, idDestino, centavos, _novaChave());
        await atualizar();
        return recibo.mensagem;
      });

  void sair([String? motivo]) {
    sessao = null;
    conta = null;
    api.token = null;
    avisoDeSessao = motivo;
    notifyListeners();
  }

  /// 401 em qualquer operacao derruba a sessao: o token de 15 min expirou.
  Future<T> _protegido<T>(Future<T> Function() operacao) async {
    try {
      return await operacao();
    } on ErroApi catch (e) {
      if (e.tokenExpirado) sair('Sessão expirada. Entre de novo.');
      rethrow;
    }
  }

  static final _aleatorio = Random.secure();

  String _novaChave() =>
      'app-${DateTime.now().microsecondsSinceEpoch}-${_aleatorio.nextInt(1 << 32).toRadixString(16)}';
}
