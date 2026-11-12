import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/modelo/dinheiro.dart';
import 'package:iceibank_mobile/modelo/malha.dart';

void main() {
  const malha = Malha(host: 'agencias');

  test(
    'fala com a agencia DONA da conta: conta 4 -> agencia 1 -> porta 4017',
    () async {
      late Uri chamada;
      final api = ApiCliente(
        malha,
        cliente: MockClient((r) async {
          chamada = r.url;
          return http.Response(
            jsonEncode({
              'id': 4,
              'nomeAluno': 'Ana',
              'saldo': 250.5,
              'agencia': 1,
              'vetorAtual': [0, 3, 0],
            }),
            200,
          );
        }),
      );

      final conta = await api.consultar(4);

      expect(chamada.toString(), 'http://agencias:4017/contas/4');
      expect(conta.saldoCentavos, 25050);
      expect(conta.vetor, [0, 3, 0]);
    },
  );

  test('envia o JWT em toda requisicao depois do login', () async {
    String? autorizacao;
    final api = ApiCliente(
      malha,
      cliente: MockClient((r) async {
        autorizacao = r.headers['Authorization'];
        return http.Response(
          jsonEncode({'id': 0, 'saldo': 1, 'agencia': 0}),
          200,
        );
      }),
    )..token = 'abc';

    await api.consultar(0);

    expect(autorizacao, 'Bearer abc');
  });

  test('erro da agencia vira ErroApi com a mensagem do backend', () async {
    final api = ApiCliente(
      malha,
      cliente: MockClient(
        (_) async => http.Response(
          jsonEncode({'erro': 'saldo insuficiente: saldo=10 valor=50'}),
          400,
        ),
      ),
    );

    expect(
      () => api.sacar(0, 5000),
      throwsA(
        isA<ErroApi>().having(
          (e) => e.mensagem,
          'mensagem',
          contains('saldo insuficiente'),
        ),
      ),
    );
  });

  test('401 e reconhecido como token expirado', () async {
    final api = ApiCliente(
      malha,
      cliente: MockClient(
        (_) async => http.Response(
          jsonEncode({'erro': 'token invalido ou expirado'}),
          401,
        ),
      ),
    );

    expect(
      () => api.consultar(0),
      throwsA(isA<ErroApi>().having((e) => e.tokenExpirado, 'expirado', true)),
    );
  });

  test('agencia fora do ar vira SemConexao, nao ErroApi', () async {
    final api = ApiCliente(
      malha,
      cliente: MockClient((_) async => throw http.ClientException('recusada')),
    );

    expect(
      () => api.consultar(2),
      throwsA(isA<SemConexao>().having((e) => e.agencia, 'agencia', 2)),
    );
  });

  test(
    'agencia que nao responde dentro do timeout tambem vira SemConexao',
    () async {
      final api = ApiCliente(
        malha,
        timeout: const Duration(milliseconds: 50),
        cliente: MockClient((_) async {
          await Future<void>.delayed(const Duration(seconds: 1));
          return http.Response('{}', 200);
        }),
      );

      expect(() => api.consultar(0), throwsA(isA<SemConexao>()));
    },
  );

  test('transferencia manda a chave de idempotencia no header', () async {
    String? chave;
    final api = ApiCliente(
      malha,
      cliente: MockClient((r) async {
        chave = r.headers['Idempotency-Key'];
        return http.Response(
          jsonEncode({'mensagem': 'ok', 'saldoOrigem': 90}),
          200,
        );
      }),
    );

    await api.transferir(0, 1, 1000, 'chave-1');

    expect(chave, 'chave-1');
  });

  group('dinheiro em centavos', () {
    test('formata no padrao brasileiro', () {
      expect(formatarReais(123456789), r'R$ 1.234.567,89');
      expect(formatarReais(5), r'R$ 0,05');
    });

    test('le valor digitado com virgula ou ponto', () {
      expect(centavosDigitados('10,5'), 1050);
      expect(centavosDigitados('10.50'), 1050);
      expect(centavosDigitados('1.000,25'), 100025);
      expect(centavosDigitados('0'), isNull);
      expect(centavosDigitados('abc'), isNull);
    });

    test('soma exata: 0,10 + 0,20 = 0,30', () {
      expect(centavosDigitados('0,10')! + centavosDigitados('0,20')!, 30);
    });
  });
}
