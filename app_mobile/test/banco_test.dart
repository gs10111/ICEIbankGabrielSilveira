import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hive_ce/hive_ce.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/dados/armazenamento.dart';
import 'package:iceibank_mobile/estado/banco.dart';
import 'package:iceibank_mobile/modelo/malha.dart';

http.Response conta(num saldo, [List<int> vetor = const [1, 0, 0]]) => http.Response(
    jsonEncode({'id': 0, 'nomeAluno': 'Ana', 'saldo': saldo, 'agencia': 0, 'vetorAtual': vetor}), 200);

late Armazenamento armazenamento;

void main() {
  setUp(() async => armazenamento = await Armazenamento.emMemoria());
  tearDown(Hive.close);

  const malha = Malha(host: 'h');

  test('login guarda o token e ja le a conta', () async {
    final banco = Banco(ApiCliente(malha, cliente: MockClient((r) async {
      if (r.url.path == '/auth/login') {
        return http.Response(jsonEncode({'token': 't', 'idConta': 0, 'nomeAluno': 'Ana', 'agencia': 0}), 200);
      }
      return conta(100);
    })), armazenamento);

    await banco.entrar(0, 'ana123');

    expect(banco.api.token, 't');
    expect(banco.conta!.saldoCentavos, 10000);
  });

  test('401 numa operacao derruba a sessao e explica o motivo', () async {
    var logado = false;
    final banco = Banco(ApiCliente(malha, cliente: MockClient((r) async {
      if (r.url.path == '/auth/login') {
        logado = true;
        return http.Response(jsonEncode({'token': 't', 'idConta': 0, 'nomeAluno': 'Ana', 'agencia': 0}), 200);
      }
      if (logado && r.url.path.endsWith('/depositar')) {
        return http.Response(jsonEncode({'erro': 'token invalido ou expirado'}), 401);
      }
      return conta(100);
    })), armazenamento);
    await banco.entrar(0, 'ana123');

    await expectLater(banco.depositar(1000), throwsA(isA<ErroApi>()));

    expect(banco.logado, isFalse);
    expect(banco.avisoDeSessao, contains('expirada'));
  });
}
