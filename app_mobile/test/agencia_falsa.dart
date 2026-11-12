import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Uma agencia em memoria que se comporta como o backend do Sprint 3: guarda saldo e
/// vetor, avanca o vetor a cada escrita e recusa /sincronizar com vetor diferente (409).
/// `online = false` simula modo aviao: toda requisicao falha antes de chegar.
class AgenciaFalsa {
  bool online = true;
  bool tokenValido = true;
  double saldo;
  List<int> vetor = [1, 0, 0];
  final List<String> aplicadas = [];

  AgenciaFalsa({this.saldo = 100});

  /// Alguem mexeu na conta por outro caminho (frontend web, Invoke-RestMethod).
  void mudarPorOutroCaminho(double deposito) {
    saldo += deposito;
    vetor = [vetor[0] + 1, vetor[1], vetor[2]];
  }

  Map<String, dynamic> get _conta =>
      {'id': 0, 'nomeAluno': 'Ana Souza', 'saldo': saldo, 'agencia': 0, 'vetorAtual': vetor};

  http.Response _json(Object corpo, [int status = 200]) => http.Response(jsonEncode(corpo), status);

  MockClient get cliente => MockClient((r) async {
        if (!online) throw http.ClientException('Network is unreachable');
        final corpo = r.body.isEmpty ? <String, dynamic>{} : jsonDecode(r.body) as Map<String, dynamic>;
        if (r.url.path == '/auth/login') {
          return _json({'token': 't', 'idConta': 0, 'nomeAluno': 'Ana Souza', 'agencia': 0});
        }
        if (!tokenValido) return _json({'erro': 'token invalido ou expirado'}, 401);
        switch (r.url.path) {
          case '/contas/0/depositar':
            mudarPorOutroCaminho((corpo['valor'] as num).toDouble());
            aplicadas.add('DEPOSITO ${corpo['valor']}');
            return _json(_conta);
          case '/contas/0/sincronizar':
            final conhecido = List<int>.from(corpo['vetorConhecido'] as List);
            if (conhecido.join(',') != vetor.join(',')) {
              return _json({
                'erro': 'Conflito detectado: o estado da conta mudou desde a ultima sincronizacao do app.',
                'vetorConhecido': conhecido,
                'vetorAtual': vetor,
                'contaAtual': _conta,
              }, 409);
            }
            final valor = (corpo['valor'] as num).toDouble();
            if (corpo['tipo'] == 'SAQUE' && valor > saldo) return _json({'erro': 'saldo insuficiente'}, 400);
            saldo += corpo['tipo'] == 'DEPOSITO' ? valor : -valor;
            vetor = [vetor[0] + 1, vetor[1], vetor[2]];
            aplicadas.add('SYNC ${corpo['tipo']} ${corpo['valor']}');
            return _json(_conta);
          default:
            return _json(_conta);
        }
      });
}
